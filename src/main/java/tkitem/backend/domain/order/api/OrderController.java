package tkitem.backend.domain.order.api;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.order.dto.request.OrderCreateRequest;
import tkitem.backend.domain.order.dto.response.OrderCreateResponse;
import tkitem.backend.domain.order.dto.response.OrderDetailResponse;
import tkitem.backend.domain.order.dto.response.OrderSummaryResponse;
import tkitem.backend.domain.order.enums.CheckoutMode;
import tkitem.backend.domain.order.service.OrderService;

@RequiredArgsConstructor
@RequestMapping("/order")
@RestController
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public OrderCreateResponse create(@AuthenticationPrincipal Member member,
                                      @RequestBody OrderCreateRequest request,
                                      @RequestParam CheckoutMode mode) {
        return orderService.createOrder(member.getMemberId(), request, mode);
    }

    @GetMapping
    public OrderSummaryResponse list(@AuthenticationPrincipal Member member) {
        return orderService.findOrdersByMemberId(member.getMemberId());
    }

    @GetMapping("/{orderId}")
    public OrderDetailResponse detail(@PathVariable Long orderId) {
        return orderService.findOrderDetail(orderId);
    }
}
