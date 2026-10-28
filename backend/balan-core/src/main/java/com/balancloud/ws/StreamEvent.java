package com.balancloud.ws;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StreamEvent {

    private String type;

    private long ts;

    private Object data;
}
