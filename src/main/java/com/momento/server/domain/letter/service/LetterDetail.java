package com.momento.server.domain.letter.service;

import com.momento.server.domain.letter.entity.Letter;

/** 본인 편지와 조회 시점의 삭제 가능 여부. 응답 변환은 Facade가 담당한다. */
public record LetterDetail(Letter letter, boolean canDelete) {}
