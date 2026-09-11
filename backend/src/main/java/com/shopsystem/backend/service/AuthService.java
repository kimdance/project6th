package com.shopsystem.backend.service;

import com.shopsystem.backend.config.AuthLockProperties;
import com.shopsystem.backend.config.SessionProperties;
import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.dto.LoginRequest;
import com.shopsystem.backend.dto.LoginResponse;
import com.shopsystem.backend.dto.MeResponse;
import com.shopsystem.backend.dto.ProfileUpdateRequest;
import com.shopsystem.backend.dto.StoreRef;
import com.shopsystem.backend.dto.TenantInfoResponse;
import com.shopsystem.backend.dto.UserRegisterRequest;
import com.shopsystem.backend.dto.UserRegisterResponse;
import com.shopsystem.backend.entity.User;
import com.shopsystem.backend.exception.BusinessException;
import com.shopsystem.backend.exception.ConflictException;
import com.shopsystem.backend.exception.UnauthorizedException;
import com.shopsystem.backend.repository.CompanyRepository;
import com.shopsystem.backend.repository.UserRepository;
import com.shopsystem.backend.security.JwtService;
import com.shopsystem.backend.web.TenantContext;
import com.shopsystem.backend.web.TenantResolutionInterceptor;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * サブドメインから解決したテナント情報の参照とログイン・トークン更新（04_architecture.md §6.1）。
 * ログイン失敗が連続 {@link AuthLockProperties#getMaxFailedAttempts()} 回に達すると
 * {@link AuthLockProperties#getLockDurationMinutes()} 分の一時ロックを行う（FR-A08）。
 * ロック解除はバッチ処理を持たず、ロック中のユーザーへの次回アクセス時にアプリ層で判定する。
 * ログイン・リフレッシュ成功のたびに最終操作時刻を更新し、
 * {@link SessionProperties#getIdleTimeoutMinutes()} 分操作がなければ次のリフレッシュを
 * 拒否して再ログインを求める（FR-A09）。対象は {@link #ROLES_SUBJECT_TO_IDLE_TIMEOUT} の
 * ロールのみで、現場スタッフ（HALL／KITCHEN／PARTTIME）はオフライン注文の運用（§9）と
 * 衝突するため対象外とする。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_LOCKED = "LOCKED";
    private static final String STATUS_RETIRED = "RETIRED";

    /** 無操作セッションタイムアウト（FR-A09）の対象ロール。現場スタッフは対象外（理由は下記参照）。 */
    private static final Set<String> ROLES_SUBJECT_TO_IDLE_TIMEOUT = Set.of("OWNER", "MANAGER");

    /**
     * ユーザー登録画面（POST /api/v1/auth/register）で自己登録できるロール。
     * 店長・オーナー等は自己登録できず、いったんスタッフ（HALL）で登録したうえで、
     * ログイン後のユーザー編集画面で昇格させる運用とする。
     */
    private static final Set<String> SELF_REGISTERABLE_ROLES = Set.of("HALL", "PARTTIME");

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** 英字と数字の両方を含む（テナント作成時のオーナーパスワードと同じ方針）。 */
    private static final Pattern PASSWORD_ALNUM = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).+$");
    private static final int PASSWORD_MIN = 8;

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MessageSource messageSource;
    private final AuthLockProperties authLockProperties;
    private final SessionProperties sessionProperties;

    public TenantInfoResponse currentTenant(HttpServletRequest request) {
        String companyCode = (String) request.getAttribute(TenantResolutionInterceptor.ATTR_COMPANY_CODE);
        String companyName = (String) request.getAttribute(TenantResolutionInterceptor.ATTR_COMPANY_NAME);
        return new TenantInfoResponse(companyCode, companyName);
    }

    /**
     * ログイン中のユーザー情報を返す（GET /api/v1/auth/me）。テナント・ユーザーの特定は
     * {@link com.shopsystem.backend.web.JwtAuthenticationInterceptor} が確立した
     * {@link TenantContext} の userId で行う。会社・店舗は遅延読み込みのため readOnly トランザクション内で解決する。
     */
    @Transactional(readOnly = true)
    public MeResponse currentUser() {
        Long userId = TenantContext.get().userId();
        User user = userRepository.findById(userId).orElseThrow(this::invalidToken);
        return toMeResponse(user);
    }

    /**
     * ログイン中の本人が、自分の氏名・メールアドレス・電話番号を変更する（PUT /api/v1/auth/me）。
     * ロール・所属店舗はここでは変更できない（経営管理者が「ユーザー管理」画面で行う）。
     */
    @Transactional
    public MeResponse updateCurrentUser(ProfileUpdateRequest req) {
        TenantContext.Data ctx = TenantContext.get();
        Locale locale = LocaleContextHolder.getLocale();
        List<ErrorItem> errors = new ArrayList<>();

        User user = userRepository.findById(ctx.userId()).orElseThrow(this::invalidToken);

        String name = trimToNull(req.getName());
        String email = normalizeLower(req.getEmail());
        String telnumber = trimToNull(req.getTelnumber());

        if (name == null) {
            errors.add(err(locale, "profile.error.name.required", "name"));
        }
        if (email == null) {
            errors.add(err(locale, "profile.error.email.required", "email"));
        } else if (!EMAIL.matcher(email).matches()) {
            errors.add(err(locale, "profile.error.email.format", "email"));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }

        if (!email.equals(user.getEmail()) && userRepository.existsByCompany_IdAndEmail(ctx.companyId(), email)) {
            throw new ConflictException(messageSource.getMessage("profile.error.email.duplicate", null, locale));
        }

        user.setName(name);
        user.setEmail(email);
        user.setTelnumber(telnumber);
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(messageSource.getMessage("profile.error.email.duplicate", null, locale));
        }

        return toMeResponse(user);
    }

    private MeResponse toMeResponse(User user) {
        return new MeResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getTelnumber(),
                user.getRole(),
                user.getCompany().getName(),
                user.getStores().stream()
                        .map(s -> new StoreRef(s.getId(), s.getName()))
                        .toList());
    }

    /**
     * 現場スタッフの自己登録（POST /api/v1/auth/register）。テナント作成・オーナー登録は
     * 運営者がPostmanで行う前提（FR-A02c）で、会社はURLサブドメインから解決する。
     * 自己登録できるロールは {@link #SELF_REGISTERABLE_ROLES} のみとし、店長・オーナー等への
     * 昇格はログイン後のユーザー編集画面で行う（本APIは未認証で呼べるため、権限昇格を防ぐ）。
     */
    @Transactional
    public UserRegisterResponse register(HttpServletRequest request, UserRegisterRequest body) {
        Long companyId = (Long) request.getAttribute(TenantResolutionInterceptor.ATTR_COMPANY_ID);
        Locale locale = LocaleContextHolder.getLocale();
        List<ErrorItem> errors = new ArrayList<>();

        String name = trimToNull(body.getName());
        String email = normalizeLower(body.getEmail());
        String password = body.getPassword();
        String telnumber = trimToNull(body.getTelnumber());
        String role = body.getRole();

        if (name == null) {
            errors.add(err(locale, "user-register.error.name.required", "name"));
        }
        if (email == null) {
            errors.add(err(locale, "user-register.error.email.required", "email"));
        } else if (!EMAIL.matcher(email).matches()) {
            errors.add(err(locale, "user-register.error.email.format", "email"));
        }
        if (password == null || password.length() < PASSWORD_MIN) {
            errors.add(err(locale, "user-register.error.password.length", "password"));
        } else if (!PASSWORD_ALNUM.matcher(password).matches()) {
            errors.add(err(locale, "user-register.error.password.format", "password"));
        }
        if (!SELF_REGISTERABLE_ROLES.contains(role)) {
            errors.add(err(locale, "user-register.error.role.invalid", "role"));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }

        // 形式チェック後に一意性を確認する（409）。事前チェックと登録の間の競合はDBのUNIQUE制約で
        // 検出し、saveAndFlush で即座に409へ変換する（TenantProvisioningServiceと同じ方針）。
        if (userRepository.existsByCompany_IdAndEmail(companyId, email)) {
            throw new ConflictException(
                    messageSource.getMessage("user-register.error.email.duplicate", null, locale));
        }

        User user = new User();
        user.setCompany(companyRepository.getReferenceById(companyId));
        // 店舗の割り当てはログイン後のユーザー編集画面で行う（初期状態は所属店舗なし）。
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setStatus(STATUS_ACTIVE);
        user.setTelnumber(telnumber);
        user.setTwoFactorEnabled(false);

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    messageSource.getMessage("user-register.error.email.duplicate", null, locale));
        }

        return new UserRegisterResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }

    // ロック処理（失敗回数のインクリメント・自動解除）は、その後 UnauthorizedException を
    // 投げてもロールバックされず確実に保存されるようにする。
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public LoginResponse login(HttpServletRequest request, LoginRequest body) {
        Long companyId = (Long) request.getAttribute(TenantResolutionInterceptor.ATTR_COMPANY_ID);
        String email = normalizeLower(body.getEmail());

        Optional<User> userOpt = email == null
                ? Optional.empty()
                : userRepository.findByCompany_IdAndEmail(companyId, email);

        if (userOpt.isEmpty()) {
            throw invalidCredentials();
        }

        User user = userOpt.get();

        // 退職済みアカウントは、ロックの自動解除（＝将来また ACTIVE に戻り得る仕組み）の対象外にする。
        // ここで即座に一般的な認証エラーとして拒否し、失敗回数のカウントアップも行わない
        // （でないと、連続失敗でロック→ロック期限切れで自動的に ACTIVE へ戻ってしまいかねない）。
        if (STATUS_RETIRED.equals(user.getStatus())) {
            throw invalidCredentials();
        }

        autoUnlockIfExpired(user);

        if (STATUS_LOCKED.equals(user.getStatus())) {
            throw locked();
        }

        boolean passwordOk = STATUS_ACTIVE.equals(user.getStatus())
                && body.getPassword() != null
                && passwordEncoder.matches(body.getPassword(), user.getPassword());

        if (!passwordOk) {
            registerFailedAttempt(user);
            throw STATUS_LOCKED.equals(user.getStatus()) ? locked() : invalidCredentials();
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastActiveAt(LocalDateTime.now());
        userRepository.save(user);

        return new LoginResponse(
                jwtService.issueAccessToken(user),
                jwtService.issueRefreshToken(user),
                "Bearer");
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public LoginResponse refresh(HttpServletRequest request, String refreshToken) {
        String companyCode = (String) request.getAttribute(TenantResolutionInterceptor.ATTR_COMPANY_CODE);

        Claims claims;
        try {
            claims = jwtService.parseRefreshToken(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw invalidToken();
        }

        if (!companyCode.equals(claims.get("companyCode", String.class))) {
            // 他テナントのリフレッシュトークンを現在のサブドメインで使わせない（04_architecture.md §3.2）。
            throw invalidToken();
        }

        Long userId = Long.valueOf(claims.getSubject());
        User user = userRepository.findById(userId).orElseThrow(this::invalidToken);
        autoUnlockIfExpired(user);

        if (!STATUS_ACTIVE.equals(user.getStatus())) {
            throw invalidToken();
        }

        if (isSessionIdleTimedOut(user)) {
            throw sessionExpired();
        }

        user.setLastActiveAt(LocalDateTime.now());
        userRepository.save(user);

        return new LoginResponse(jwtService.issueAccessToken(user), refreshToken, "Bearer");
    }

    /**
     * 最終操作時刻からの経過が設定を超えていれば無操作タイムアウトとみなす（FR-A09）。
     * 現場スタッフ（HALL／KITCHEN／PARTTIME）のログインは対象外とする。スタッフ端末はオフライン中に
     * 注文を貯め込み、オンライン復帰後まとめて送信する運用（§9）があり、オフライン許容時間の上限は
     * フェーズ1では設けない方針のため、リフレッシュ間隔の空きだけでセッション切れにしてしまうと
     * この運用と衝突する。設定変更・売上確認等を行う OWNER／MANAGER のみ対象とする。
     */
    private boolean isSessionIdleTimedOut(User user) {
        if (!ROLES_SUBJECT_TO_IDLE_TIMEOUT.contains(user.getRole())) {
            return false;
        }
        if (user.getLastActiveAt() == null) {
            return false;
        }
        Duration idle = Duration.between(user.getLastActiveAt(), LocalDateTime.now());
        return idle.compareTo(Duration.ofMinutes(sessionProperties.getIdleTimeoutMinutes())) > 0;
    }

    /** ロック期限を過ぎていれば ACTIVE へ戻し、失敗回数をリセットする。 */
    private void autoUnlockIfExpired(User user) {
        if (STATUS_LOCKED.equals(user.getStatus())
                && user.getLockedUntil() != null
                && !user.getLockedUntil().isAfter(LocalDateTime.now())) {
            user.setStatus(STATUS_ACTIVE);
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }

    /** 失敗回数を1増やし、しきい値に達したらロックする。 */
    private void registerFailedAttempt(User user) {
        int count = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(count);
        if (count >= authLockProperties.getMaxFailedAttempts()) {
            user.setStatus(STATUS_LOCKED);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(authLockProperties.getLockDurationMinutes()));
        }
        userRepository.save(user);
    }

    private UnauthorizedException invalidCredentials() {
        Locale locale = LocaleContextHolder.getLocale();
        return new UnauthorizedException(
                messageSource.getMessage("auth.error.invalid-credentials", null, locale));
    }

    private UnauthorizedException locked() {
        Locale locale = LocaleContextHolder.getLocale();
        return new UnauthorizedException(
                messageSource.getMessage("auth.error.locked", null, locale));
    }

    private UnauthorizedException invalidToken() {
        Locale locale = LocaleContextHolder.getLocale();
        return new UnauthorizedException(
                messageSource.getMessage("auth.error.invalid-token", null, locale));
    }

    private UnauthorizedException sessionExpired() {
        Locale locale = LocaleContextHolder.getLocale();
        return new UnauthorizedException(
                messageSource.getMessage("auth.error.session-expired", null, locale));
    }

    private static String normalizeLower(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t.toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private ErrorItem err(Locale locale, String code, String field) {
        return new ErrorItem(messageSource.getMessage(code, null, locale), List.of(field));
    }
}
