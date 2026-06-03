package org.avidd.lsm;

public enum OpType {
  PUT(0),
  DELETE(1);

  private OpType(int ordinal) {
    assert this.ordinal() == ordinal;
  }
}
