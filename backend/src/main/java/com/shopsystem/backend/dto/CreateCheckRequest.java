package com.shopsystem.backend.dto;

import lombok.Data;

import java.util.List;

/**
 * POST /api/v1/stores/{storeId}/table-sessions/{sessionId}/checks のリクエストボディ（FR-G01・G11）。
 * orderLineIds を省略（null／空）すると、セッション内のまだどの会計にも割り当てられていない
 * 明細（取消・却下を除く）をすべて対象にする。別会計（FR-G11）にする場合のみ明示的に指定する。
 */
@Data
public class CreateCheckRequest {
    private List<Long> orderLineIds;
}
