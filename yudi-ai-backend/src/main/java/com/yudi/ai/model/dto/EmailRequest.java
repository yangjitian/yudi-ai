package com.yudi.ai.model.dto;


import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class EmailRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -1039943911699338786L;

    private String email;
}
