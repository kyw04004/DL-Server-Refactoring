package com.nerdnull.donlate.server.controller;

import com.nerdnull.donlate.server.controller.request.AllocateRequest;
import com.nerdnull.donlate.server.controller.request.ExchangeRequest;
import com.nerdnull.donlate.server.controller.response.GetPaymentListResponse;
import com.nerdnull.donlate.server.controller.response.Response;
import com.nerdnull.donlate.server.dto.ExchangeDto;
import com.nerdnull.donlate.server.dto.PaymentDto;
import com.nerdnull.donlate.server.dto.PlanDto;
import com.nerdnull.donlate.server.dto.PlanStateDto;
import com.nerdnull.donlate.server.service.ExchangeService;
import com.nerdnull.donlate.server.service.PaymentService;
import com.nerdnull.donlate.server.service.PlanService;
import com.nerdnull.donlate.server.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@RequestMapping("/api/v1/finance")
@RestController
@Slf4j
public class FinanceController {
    private final ExchangeService exchangeService;
    private final PaymentService paymentService;
    private final UserService userService;
    private final PlanService planService;

    @Autowired
    public FinanceController(ExchangeService exchangeService, PaymentService paymentService, UserService userService, PlanService planService) {
        this.exchangeService = exchangeService;
        this.paymentService = paymentService;
        this.userService = userService;
        this.planService = planService;
    }

    @PostMapping("/exchange")
    public Response<String> exchange(@RequestBody ExchangeRequest request){
        try {
            request.isNotNull();

            ExchangeDto exchangeDto = new ExchangeDto(null, request.getBank(), request.getAccount(), request.getPoint(), request.getName(), null);
            this.exchangeService.save(exchangeDto);

            PaymentDto paymentDto = new PaymentDto(null, 0L, request.getPoint(), new Date(), request.getUserId(), null);
            this.paymentService.add(paymentDto);

            this.userService.updatePoint(request.getUserId(), -request.getPoint());

            return Response.ok("Exchange request complete");
        }
        catch (IllegalAccessException | IllegalArgumentException e){
            log.error(e.getMessage(), e);
            return Response.error(Response.BAD_REQUEST, e.getMessage());
        }
        catch (Exception e){
            log.error(e.getMessage(), e);
            return Response.error(Response.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/payment/{userId}/list")
    public Response <GetPaymentListResponse> getPaymentList(@PathVariable Long userId){
        try {
            if(userId == null) {
                throw new IllegalArgumentException("Bad Request /api/v1/user/<Long> userID");
            }

            GetPaymentListResponse response = new GetPaymentListResponse(new ArrayList<>());
            List<PaymentDto> paymentList = this.paymentService.findByUserId(userId);
            for(PaymentDto payment : paymentList){
                response.add(payment.getUserId(), payment.getPaymentId(), payment.getMoney(), payment.getPoint(), payment.getDate());
            }

            return Response.ok(response);
        }
        catch (IllegalArgumentException e){
            log.error(e.getMessage(), e);
            return Response.error(Response.BAD_REQUEST, e.getMessage());
        }
        catch (Exception e){
            log.error(e.getMessage(), e);
            return Response.error(Response.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping("/allocate")
    public Response<String> allocate(@RequestBody AllocateRequest request){
        try {
            request.isNotNull();
            PlanDto plan = this.planService.getDetails(request.getPlanId());
            Long deposit = plan.getDeposit();
            List<PlanStateDto> planStateList = plan.getPlanStateList();
            long lateToNormal = deposit * plan.getLatePercent() / 100;
            long absentToNormal = deposit * plan.getAbsentPercent() / 100;
            long forNormalPerson = 0L;
            int normalCnt = 0;

            for (PlanStateDto p : planStateList) {
                Integer lateState = p.getLateState();
                if (lateState == 1) forNormalPerson += lateToNormal;
                else if (lateState == 2) forNormalPerson += absentToNormal;
                else normalCnt++;
            }

            Long toNormalPerson = (forNormalPerson / normalCnt);

            for (PlanStateDto p : planStateList) {
                Integer lateState = p.getLateState();
                Long userId = p.getUserId();
                if (lateState == 1) this.userService.updatePoint(userId, deposit - lateToNormal);
                else if (lateState == 2) this.userService.updatePoint(userId, deposit - absentToNormal);
                else userService.updatePoint(userId, deposit+toNormalPerson);
            }
        }

        catch (IllegalArgumentException e){
            log.error(e.getMessage(), e);
            return Response.error(Response.BAD_REQUEST, e.getMessage());
        }
        catch (Exception e){
            log.error(e.getMessage(), e);
            return Response.error(Response.INTERNAL_SERVER_ERROR, e.getMessage());
        }
        return Response.ok("allocate complete");
    }

}
