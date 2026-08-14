package com.shopsystem.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@MappedSuperclass
@Data
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    // 登録日時
    @Column(nullable = false, updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    // 登録者
    @Column(nullable = false, updatable = false)
    @CreatedBy
    private String createdBy;

    // 更新日時
    @LastModifiedDate
    private LocalDateTime updatedAt;

    // 更新者
    @LastModifiedBy
    private String updatedBy;
}
