package com.chatactor.controllers;

import com.chatactor.model.ChatLine;
import com.chatactor.store.ChatStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatHistoryController {

    @GetMapping("/{roomId}/history")
    public List<ChatLine> history(@PathVariable String roomId) {
        return ChatStore.get().history(roomId);
    }
}
