package com.seowon.coding.controller;

import com.seowon.coding.domain.dto.OrderDto;
import com.seowon.coding.domain.model.Order;
import com.seowon.coding.service.OrderProduct;
import com.seowon.coding.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    
    private final OrderService orderService;
    
    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Order> updateOrder(@PathVariable Long id, @RequestBody Order order) {
        try {
            Order updatedOrder = orderService.updateOrder(id, order);
            return ResponseEntity.ok(updatedOrder);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        try {
            orderService.deleteOrder(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * TODO #2: 주문을 생성하는 API 구현
     * 구현목록:
     * 1. Request DTO 를 받아서 주문 생성
     * 2. orderService.placeOrder 호출
     * 3. 주문 생성시 HTTP 201 CREATED 반환
     * 4. 필요한 DTO 생성
     * 
     * Request body 예시:
     * {
     *   "customerName": "John Doe",
     *   "customerEmail": "john@example.com",
     *   "products": [
     *     {"productId": 1, "quantity": 2},
     *     {"productId": 3, "quantity": 1}
     *   ]
     * }
     */
    @PostMapping("/")
    public ResponseEntity<Order> placeOrder(@RequestBody OrderDto orderDto) {
        try {
            // DTO에서 데이터 추출
            List<Long> productsId = orderDto.getProducts().stream()
                    .map(OrderProduct::getProductId)
                    .toList();
            List<Integer> quantities = orderDto.getProducts().stream()
                    .map(OrderProduct::getQuantity)
                    .toList();

            Order order = orderService.placeOrder(
                    orderDto.getCustomerName(),
                    orderDto.getCustomerEmail(),
                    productsId,
                    quantities
            );
            return new ResponseEntity<>(order, HttpStatus.CREATED);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }













    //
}