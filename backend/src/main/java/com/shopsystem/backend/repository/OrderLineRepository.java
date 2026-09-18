package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.OrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    // registeredAt だけだと、同じ注文送信でまとめて登録された明細は登録時刻が完全に同じになり
    // 順序が不安定になる（提供済み・取消等の更新後に並びが変わって見える不具合の原因だった）ため、
    // id を第2キーにして常に登録順を安定させる。
    List<OrderLine> findAllByTableSession_IdOrderByRegisteredAtAscIdAsc(Long tableSessionId);

    List<OrderLine> findAllByOrder_Id(Long orderId);

    Optional<OrderLine> findByIdAndTableSession_Store_Id(Long id, Long storeId);
}
