package com.momento.server.domain.letter.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "편지 부분 수정 요청. 생략한 필드는 유지하며 빈 객체는 변경 없이 반환한다.")
public class LetterUpdateRequest {
  @Schema(description = "본문. 생략 시 유지, null 불가. DRAFT만 빈 본문을 허용한다.")
  @Size(max = 10000, message = "편지 본문은 10000자 이하여야 합니다.")
  private String content;

  @Schema(description = "편지지 테마. 생략 시 유지, null이면 선택을 해제한다.", nullable = true)
  @Size(max = 20, message = "편지지 테마는 20자 이하여야 합니다.")
  private String themeType;

  @JsonIgnore private boolean contentPresent;
  @JsonIgnore private boolean themeTypePresent;

  @JsonSetter(value = "content", nulls = Nulls.FAIL)
  public void setContent(String content) {
    this.content = content;
    this.contentPresent = true;
  }

  @JsonSetter(value = "themeType", nulls = Nulls.SET)
  public void setThemeType(String themeType) {
    this.themeType = themeType;
    this.themeTypePresent = true;
  }

  public String getContent() {
    return content;
  }

  public String getThemeType() {
    return themeType;
  }

  public boolean hasContent() {
    return contentPresent;
  }

  public boolean hasThemeType() {
    return themeTypePresent;
  }
}
