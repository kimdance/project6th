package com.shopsystem.backend.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** {@link TableSessionTable} の複合主キー（table_session_id, dining_table_id）。 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableSessionTableId implements Serializable {
    private Long tableSessionId;
    private Long diningTableId;
}
