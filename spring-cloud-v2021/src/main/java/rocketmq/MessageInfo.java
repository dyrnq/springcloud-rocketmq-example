package rocketmq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;


@Data
@AllArgsConstructor
@Builder
public class MessageInfo {
    private Integer index;
    private String body;
}
