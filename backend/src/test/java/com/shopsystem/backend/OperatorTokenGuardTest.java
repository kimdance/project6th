package com.shopsystem.backend;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shopsystem.backend.config.OperatorProperties;
import com.shopsystem.backend.exception.ForbiddenException;
import com.shopsystem.backend.security.OperatorTokenGuard;

import org.junit.jupiter.api.Test;

class OperatorTokenGuardTest {

    private OperatorTokenGuard guardWithToken(String configured) {
        OperatorProperties props = new OperatorProperties();
        props.setProvisionToken(configured);
        return new OperatorTokenGuard(props);
    }

    @Test
    void 合言葉が未設定なら常に拒否() {
        OperatorTokenGuard guard = guardWithToken(null);
        assertThatThrownBy(() -> guard.verify("anything")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> guard.verify(null)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 合言葉が空文字でも拒否() {
        OperatorTokenGuard guard = guardWithToken("   ");
        assertThatThrownBy(() -> guard.verify("   ")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 設定済みで不一致なら拒否_一致なら通す() {
        OperatorTokenGuard guard = guardWithToken("s3cret-token");
        assertThatThrownBy(() -> guard.verify(null)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> guard.verify("wrong")).isInstanceOf(ForbiddenException.class);
        assertThatCode(() -> guard.verify("s3cret-token")).doesNotThrowAnyException();
    }
}
