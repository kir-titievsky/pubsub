// Copyright 2016 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////
package com.google.pubsub.flic.processing;

import com.google.pubsub.flic.common.MessagePacketProto.MessagePacket;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Takes two files containing MessagePacket data and compares the contents of the files to see if
 * the frequency of each message is the same.
 */
public class Comparison {

  private static final Logger log = LoggerFactory.getLogger(Comparison.class.getName());
  private static final String COMPARISON_SUCCESS = "Comparison test passed!!";
  private static final String COMPARISON_FAIL = "Comparison test failed.";

  private File f1;
  private File f2;

  public Comparison(String s1, String s2) {
    this.f1 = new File(s1);
    this.f2 = new File(s2);
  }

  /** Compares histograms of the two files and logs whether they were the same. */
  public void compare() throws Exception {
    Map<MessagePacket, MutableInt> histogram1 = createHistogram(f1);
    Map<MessagePacket, MutableInt> histogram2 = createHistogram(f2);
    if (histogram1.size() == 0 || histogram2.size() == 0) {
      log.error(COMPARISON_FAIL);
      return;
    }
    for (Object key : histogram1.keySet()) {
      if (histogram2.get(key) == null) {
        log.error(COMPARISON_FAIL);
      }
      if (histogram2.get(key).intValue() != histogram1.get(key).intValue()) {
        log.error(COMPARISON_FAIL);
        return;
      }
    }
    log.info(COMPARISON_SUCCESS);
  }

  /** Create a histogram of frequencies of MessagePacket's from a file. */
  private Map<MessagePacket, MutableInt> createHistogram(File f) throws Exception {
    Map<MessagePacket, MutableInt> histogram = new HashMap<>();
    MessagePacket packet;
    FileInputStream is = new FileInputStream(f);
    while ((packet = MessagePacket.parseDelimitedFrom(is)) != null) {
      if (histogram.containsKey(packet)) {
        histogram.get(packet).increment();
      } else {
        histogram.put(packet, new MutableInt(1));
      }
    }
    return histogram;
  }
}
