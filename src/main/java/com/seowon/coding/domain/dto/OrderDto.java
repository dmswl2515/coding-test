package com.seowon.coding.domain.dto;

import com.seowon.coding.service.OrderProduct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 모든 필드 생성자
public class OrderDto {
    private String customerName;
    private String customerEmail;

    private List<OrderProduct> products = new ArrayList<>();


}
