package com.chatactor.model;

public record ChatLine(String userId, String roomId, String message, long ts) {}
