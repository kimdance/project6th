package com.shopsystem.backend.web;

import com.shopsystem.backend.entity.Company;
import com.shopsystem.backend.exception.TenantNotFoundException;
import com.shopsystem.backend.repository.CompanyRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Locale;

/**
 * 未認証の認証系エンドポイント（/api/v1/auth/**）向けに、リクエストの `Host` ヘッダ先頭ラベルを
 * company_code とみなしてテナントを解決する（04_architecture.md §6.1／§3.2）。
 * 見つからない場合（www・apex・サブドメインなし・存在しないコードを含む）は一律404とし、
 * テナントの存在有無を漏らさない。見つかった場合は HttpSession とリクエスト属性の両方に保持する。
 */
@Component
@RequiredArgsConstructor
public class TenantResolutionInterceptor implements HandlerInterceptor {

    public static final String ATTR_COMPANY_ID = "tenant.companyId";
    public static final String ATTR_COMPANY_CODE = "tenant.companyCode";
    public static final String ATTR_COMPANY_NAME = "tenant.companyName";

    private final CompanyRepository companyRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String companyCode = extractCompanyCode(request.getHeader("Host"));
        Company company = companyRepository.findByCompanyCode(companyCode)
                .orElseThrow(TenantNotFoundException::new);

        request.setAttribute(ATTR_COMPANY_ID, company.getId());
        request.setAttribute(ATTR_COMPANY_CODE, company.getCompanyCode());
        request.setAttribute(ATTR_COMPANY_NAME, company.getName());

        HttpSession session = request.getSession(true);
        session.setAttribute(ATTR_COMPANY_ID, company.getId());
        session.setAttribute(ATTR_COMPANY_CODE, company.getCompanyCode());
        session.setAttribute(ATTR_COMPANY_NAME, company.getName());
        return true;
    }

    /** Host ヘッダの先頭ラベルを company_code とみなす（ポート番号・大文字小文字は無視）。 */
    static String extractCompanyCode(String host) {
        if (host == null || host.isBlank()) {
            return "";
        }
        String withoutPort = host.split(":", 2)[0];
        int dot = withoutPort.indexOf('.');
        String label = dot < 0 ? withoutPort : withoutPort.substring(0, dot);
        return label.toLowerCase(Locale.ROOT);
    }
}
