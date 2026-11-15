package com.dyrnq.sca.rocketmq;

public class MessageInfo {
  private Integer index;
  private String body;

  public MessageInfo() {}

  public MessageInfo(Integer index, String body) {
    this.index = index;
    this.body = body;
  }

  public Integer getIndex() {
    return index;
  }

  public void setIndex(Integer index) {
    this.index = index;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }
}
