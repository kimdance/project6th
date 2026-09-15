package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * ログイン後の共通トップ画面（ホーム）に並べる機能の入口。docs/04_architecture.md の
 * app_feature に対応。company/store に紐づかない、アプリ全体で共通の設定。
 * 名前は既存の menu_item（飲食メニュー）と紛らわしいため区別している。
 */
@Entity
@Table(name = "app_feature")
@Data
@EqualsAndHashCode(callSuper = true)
public class AppFeature extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 画面遷移先の識別子（例 'store'）。フロントの表示用キーではなく、内部識別子。 */
    @Column(name = "feature_key", nullable = false, unique = true, length = 50)
    private String featureKey;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 255)
    private String description;

    /** フロントのルーティングパス（例 '/settings/store'）。 */
    @Column(nullable = false, length = 255)
    private String path;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * 自テナントに店舗が1件も無い間は非表示にするかどうか。テナント登録直後（経営管理者は
     * 作成済みだが店舗は未作成）は、店舗が無いと押しても先に進めない機能を隠すために使う。
     */
    @Column(name = "requires_store", nullable = false)
    private boolean requiresStore;

    /** この機能の入口を表示するロール（OWNER/MANAGER/HALL/KITCHEN/PARTTIME）。 */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_feature_role", joinColumns = @JoinColumn(name = "app_feature_id"))
    @Column(name = "role", nullable = false, length = 20)
    private Set<String> roles = new LinkedHashSet<>();
}
