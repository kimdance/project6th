package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.ReservationNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservationNotificationRepository extends JpaRepository<ReservationNotification, Long> {
}
