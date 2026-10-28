package com.balancloud.command.event;

import com.balancloud.command.domain.DeviceCommand;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommandResultEvent {

    private DeviceCommand command;

    private String result;
}
