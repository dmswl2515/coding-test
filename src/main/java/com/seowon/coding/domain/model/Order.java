package com.seowon.coding.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders") // "order" is a reserved keyword in SQL
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String customerName;
    
    private String customerEmail;

    private Long productId;

    private Integer quantity;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    
    private LocalDateTime orderDate;
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();
    
    private BigDecimal totalAmount;
    
    // Business logic
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
        recalculateTotalAmount();
    }
    
    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
        recalculateTotalAmount();
    }
    
    public void recalculateTotalAmount() {
        this.totalAmount = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void addProduct(Product product, int quantity) {
        // 1. 검증
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
        if (product.getStockQuantity() < quantity) {
            throw new IllegalStateException("insufficient stock for product " + product.getId());
        }

        // 2. OrderItem 생성
        OrderItem item = OrderItem.builder()
                .order(this)
                .product(product)
                .quantity(quantity)
                .price(product.getPrice())
                .build();
        // 3. 추가(자동으로 총액 재계산)
        this.addItem(item);

        // 4. 재고 감소
        product.decreaseStock(quantity);
    }

    public void applyShippingAndDiscount(String couponCode) {
        // 현재 총액 (상품 합계)
        BigDecimal itemsTotal = this.totalAmount;

        // 배송비 계산
        BigDecimal shipping = itemsTotal.compareTo(new BigDecimal("100.00")) >= 0
                                                    ? BigDecimal.ZERO
                                                    : new BigDecimal("5.00");

        // 할인 계산
        BigDecimal discount = (couponCode != null && couponCode.startsWith("SALE"))
                                ? new BigDecimal("10.00")
                                : BigDecimal.ZERO;

        // 최종 금액 = 상품 합계 + 배송비 - 할인
        this.totalAmount = itemsTotal.add(shipping).subtract(discount);

    }
    
    public void markAsProcessing() {
        this.status = OrderStatus.PROCESSING;
    }
    
    public void markAsShipped() {
        this.status = OrderStatus.SHIPPED;
    }
    
    public void markAsDelivered() {
        this.status = OrderStatus.DELIVERED;
    }
    
    public void markAsCancelled() {
        this.status = OrderStatus.CANCELLED;
    }
    
    public enum OrderStatus {
        PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED
    }
}