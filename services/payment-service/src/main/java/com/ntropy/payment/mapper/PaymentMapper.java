package com.ntropy.payment.mapper;

import com.ntropy.payment.domain.Payment;
import com.ntropy.payment.domain.PaymentStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PaymentMapper {

    Payment findById(Long paymentId);


    List<Payment> findAllBySubscriptionId(Long subscriptionId);

    int insert(Payment payment);

    int updateStatus(@Param("paymentId") Long paymentId,
                     @Param("paymentStatus") PaymentStatus paymentStatus,
                     @Param("receiptUrl") String receiptUrl);
}