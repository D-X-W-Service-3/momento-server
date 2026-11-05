package com.momento.server.domain.push.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PushEndpointTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://fcm.googleapis.com/fcm/send/abc:APA91b",
        "https://updates.push.services.mozilla.com/wpush/v2/gAAAA",
        "https://web.push.apple.com/QGuQyavXutnMH",
        "https://wns2-par02p.notify.windows.com/w/?token=BQYAAA",
        "https://FCM.googleapis.com/fcm/send/abc",
        "https://fcm.googleapis.com:443/fcm/send/abc"
      })
  void allowsBrowserPushServices(String endpoint) {
    assertThat(PushEndpoint.isAllowed(endpoint)).isTrue();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {
        "",
        "http://fcm.googleapis.com/fcm/send/abc",
        "https://169.254.169.254/latest/meta-data",
        "https://localhost:8080/v1/users",
        "https://evil.com/fcm.googleapis.com",
        "https://fcm.googleapis.com.evil.com/send",
        "https://fcm.googleapis.com@evil.com/send",
        "https://user@fcm.googleapis.com/send",
        "https://fcm.googleapis.com:8443/send",
        "https://notify.windows.com.evil.com/w",
        "https://evilnotify.windows.com/w",
        "ftp://fcm.googleapis.com/send",
        "not a url"
      })
  void rejectsOtherAddresses(String endpoint) {
    assertThat(PushEndpoint.isAllowed(endpoint)).isFalse();
  }
}
