package com.seowon.coding.service;

import com.seowon.coding.domain.model.Order;
import com.seowon.coding.domain.model.ProcessingStatus;
import com.seowon.coding.domain.model.Product;
import com.seowon.coding.domain.repository.OrderRepository;
import com.seowon.coding.domain.repository.ProcessingStatusRepository;
import com.seowon.coding.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProcessingStatusRepository processingStatusRepository;
    private final ProcessingStatusService processingStatusService;
    
    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
    
    @Transactional(readOnly = true)
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }
    

    public Order updateOrder(Long id, Order order) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found with id: " + id);
        }
        order.setId(id);
        return orderRepository.save(order);
    }
    
    public void deleteOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found with id: " + id);
        }
        orderRepository.deleteById(id);
    }



    public Order placeOrder(String customerName,
                            String customerEmail,
                            List<Long> productIds,
                            List<Integer> quantities) {
        // TODO #3: 구현 항목
        // * placeOrder 메소드의 시그니처는 변경하지 않은 채 구현하세요.
        if(customerName == null || customerEmail == null) {
            throw new IllegalArgumentException("customer info required");
        }
        // * 주어진 고객 정보로 새 Order를 생성
        Order order = Order.builder()
                .customerName(customerName)
                .customerEmail(customerEmail)
                .status(Order.OrderStatus.PENDING) // * order 의 상태를 PENDING 으로 변경
                .orderDate(LocalDateTime.now())    // * orderDate 를 현재시간으로 설정
                .items(new ArrayList<>())
                .totalAmount(BigDecimal.ZERO)
                .build();


        // * 지정된 Product를 주문에 추가
        for (int i = 0; i < productIds.size() ; i++) {
            Long pid = productIds.get(i);

            Product product = productRepository.findById(pid)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + pid));

            order.addProduct(product, quantities.get(i));
        }
        return orderRepository.save(order);
    }

    /**
     * TODO #4 (리펙토링): Service 에 몰린 도메인 로직을 도메인 객체 안으로 이동
     * - Repository 조회는 도메인 객체 밖에서 해결하여 의존 차단 합니다.
     * - #3 에서 추가한 도메인 메소드가 있을 경우 사용해도 됩니다.
     */
    public Order checkoutOrder(String customerName,
                               String customerEmail,
                               List<OrderProduct> orderProducts,
                               String couponCode) {
        if (customerName == null || customerEmail == null) {
            throw new IllegalArgumentException("customer info required");
        }
        if (orderProducts == null || orderProducts.isEmpty()) {
            throw new IllegalArgumentException("orderReqs invalid");
        }

        Order order = Order.builder()
                .customerName(customerName)
                .customerEmail(customerEmail)
                .status(Order.OrderStatus.PENDING)
                .orderDate(LocalDateTime.now())
                .items(new ArrayList<>())
                .totalAmount(BigDecimal.ZERO)
                .build();


        for (OrderProduct req : orderProducts) {
            Long pid = req.getProductId();
            int qty = req.getQuantity();

            Product product = productRepository.findById(pid)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + pid));
            order.addProduct(product, qty);
        }

        order.applyShippingAndDiscount(couponCode);
        order.markAsProcessing();

        return orderRepository.save(order);
    }

    /**
     * TODO #5: 코드 리뷰 - 장시간 작업과 진행률 저장의 트랜잭션 분리
     * - 시나리오: 일괄 배송 처리 중 진행률을 저장하여 다른 사용자가 조회 가능해야 함.
     * - 리뷰 포인트: proxy 및 transaction 분리, 예외 전파/롤백 범위, 가독성 등
     * - 상식적인 수준에서 요구사항(기획)을 가정하며 최대한 상세히 작성하세요.
     * 1. 프록시 문제
     * - 문제 : this.updateProgressRequiresNew() 호출시 Spring Proxy를 거치지 않아,
     *         @Transactional(REQUIRES_NEW) 무시됨
     * - 원인 : 같은 클래스 내부에서 this로 메서드 호출시 프록시를 우회하여 직접 호출됨
     * - 영향 : 진행률이 부모 트랜잭션에 포함되어 전체 작업 완료 전까지 다른 사용자가 조회 불가
     * - 해결 : ProcessingStatusService로 분리하여 별도 Bean을 통해 호출하도록 수정
     *
     * 2. 예외 처리 문제
     * - 문제 : catch(Exception e) {} 블록에서 예외를 조용히 무시(Slient Failure)
     * - 영향 : a) 어떤 주문이 실패했는지 추척 불가(로그 없음)
     *         b) 사용자는 "100개 처리 완료"로 보이지만, 실제로는 일부만 성공했을 수 있음
     *         c) 장애 발생 시 원인 파악 및 디버깅 불가능
     * - 개선 방안 : a) 로그 추가 : log.error("Failed to process order: {}", orderId, e)
     *             b) 실패 카운트 추적 : failed 변수 추가
     *             c) 진행률에 성공/실패 건수 모두 포함
     *
     * 3. 트랜젝션 롤백 범위 문제
     * - 문제 : 전체 메서드가 하나의 @Transactional로 묶여 있음
     * - 영향 : a) 999개 성공 후 1000번 째 주문에서 예외 발생시 전체 롤백
     *         b) 진행률 저장(markRunning, markCompleted)도 함께 롤백될 수 있음
     *         c) 장시간 작업 중 DB 커넥션 점유
     * - 개선 방안 : a) 각 주문 처리를 별도 트랜잭션으로 분리(REQUIRES_NEW)
     *             b) 또는 전체를 readOnly=true로 변경하고 각 저장은 별도 트랜잭션 사용
     *             c) 부분 실패를 허용하는 설계 (resilient design)
     *
     * 4. 가독성 및 코드 품질 문제
     * - 문제 : null 체크 : (orderIds == null ? List.<Long>of() : orderIds)
     * - 개선 : 작업 성공 후 processed++ 처리하여 의도 명확화
     *
     * 5. 데이터 정합성 문제
     * - 문제 : ps = processingStatusRepository.findByJobId(jobId).orElse(ps)
     * - 영향 : 동시성 환경에서 다른 스레드가 ProcessingStatus 수정 시 Lost Update 발생 가능
     * - 개선 방안 : a) 최신 데이터를 조회 후 업데이트
     *             b) 낙관적 락(@Version)또는 비관적 락 사용 고려
     *
     * 6. 성능 문제
     * - 문제 : orderRepository.findById(orderId) > 주문 하나씩 조회 (N+1)
     * - 개선 방안 : findAllById()로 배치 조회 후 처리
     *
     */
    @Transactional
    public void bulkShipOrdersParent(String jobId, List<Long> orderIds) {
        ProcessingStatus ps = processingStatusRepository.findByJobId(jobId)
                .orElseGet(() -> processingStatusRepository.save(ProcessingStatus.builder().jobId(jobId).build()));
        ps.markRunning(orderIds == null ? 0 : orderIds.size());
        processingStatusRepository.save(ps);

        int processed = 0;
        for (Long orderId : (orderIds == null ? List.<Long>of() : orderIds)) {
            try {
                // 오래 걸리는 작업 이라는 가정 시뮬레이션 (예: 외부 시스템 연동, 대용량 계산 등)
                orderRepository.findById(orderId).ifPresent(o -> o.setStatus(Order.OrderStatus.PROCESSING));

                processingStatusService.updateProgress(jobId, ++processed, orderIds.size());
            } catch (Exception e) {
            }
        }
        ps = processingStatusRepository.findByJobId(jobId).orElse(ps);
        ps.markCompleted();
        processingStatusRepository.save(ps);
    }
}