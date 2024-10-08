package com.housing.back.dto.response.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.housing.back.common.ResponseCode;
import com.housing.back.common.ResponseMessage;
import com.housing.back.dto.response.ResponseDto;

import lombok.Getter;

@Getter
public class NicknameResponseDto extends ResponseDto {
    private String nickname;
    private String message;

    private NicknameResponseDto(){
        super();
    }

    public NicknameResponseDto(String nickname, String message) {
        super();
        this.nickname = nickname;
        this.message = message;
    }

    public static ResponseEntity<NicknameResponseDto> success(String nickname) {
        NicknameResponseDto responseBody = new NicknameResponseDto(nickname, ResponseMessage.SUCCESS);
        return ResponseEntity.status(HttpStatus.OK).body(responseBody);
    }

    public static ResponseEntity<ResponseDto> duplicatedNickname() {
        ResponseDto responseBody = new ResponseDto(ResponseCode.DUPLICATE_ID, ResponseMessage.DUPLICATE_ID);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseBody);
    }
    
    public static ResponseEntity<NicknameResponseDto> noNickname() {
        NicknameResponseDto responseBody = new NicknameResponseDto(null, "NOT FOUND NICKNAME");
        return ResponseEntity.status(HttpStatus.OK).body(responseBody);
    }
}
