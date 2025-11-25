package com.yudi.ai.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class ChatRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 173704846133947637L;

    private String query;

    private String mode;

    private String conversationId;

}
