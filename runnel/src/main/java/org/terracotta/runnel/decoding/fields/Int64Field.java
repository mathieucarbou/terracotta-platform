/*
 * Copyright Terracotta, Inc.
 * Copyright Super iPaaS Integration LLC, an IBM Company 2024
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terracotta.runnel.decoding.fields;

import org.terracotta.runnel.utils.CorruptDataException;
import org.terracotta.runnel.utils.ReadBuffer;

/**
 * @author Ludovic Orban
 */
public class Int64Field extends AbstractValueField<Long> {

  public Int64Field(String name, int index) {
    super(name, index);
  }

  @Override
  public Long decode(ReadBuffer readBuffer) {
    int size = readBuffer.getVlqInt();
    if (size != 8) {
      throw new CorruptDataException("Expected field size of 8, read : " + size);
    }
    return readBuffer.getLong();
  }

}
