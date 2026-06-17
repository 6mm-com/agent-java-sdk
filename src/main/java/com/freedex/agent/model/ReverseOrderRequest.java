package com.freedex.agent.model;

public class ReverseOrderRequest {
    public String origOrderNo;
    public String reverseOrderNo;
    public String reverseReason;

    public static ReverseOrderRequest of(String origOrderNo, String reverseOrderNo, String reverseReason) {
        ReverseOrderRequest request = new ReverseOrderRequest();
        request.origOrderNo = origOrderNo;
        request.reverseOrderNo = reverseOrderNo;
        request.reverseReason = reverseReason;
        return request;
    }
}
