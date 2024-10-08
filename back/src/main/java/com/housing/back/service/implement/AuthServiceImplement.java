package com.housing.back.service.implement;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.housing.back.common.CertificationNumber;
import com.housing.back.common.IpUtils;
import com.housing.back.common.JwtUtils;
import com.housing.back.common.ResponseCode;
import com.housing.back.common.ResponseMessage;
import com.housing.back.dto.request.auth.CheckCertificationRequestDto;
import com.housing.back.dto.request.auth.EmailCertificationRequestDto;
import com.housing.back.dto.request.auth.IdCheckRequestDto;
import com.housing.back.dto.request.auth.NicknameRequestDto;
import com.housing.back.dto.request.auth.SignInRequestDto;
import com.housing.back.dto.request.auth.SignUpRequestDto;
import com.housing.back.dto.response.ResponseDto;
import com.housing.back.dto.response.TestResponseDto;
import com.housing.back.dto.response.auth.CheckCertificationResponseDto;
import com.housing.back.dto.response.auth.EmailCertificationResponseDto;
import com.housing.back.dto.response.auth.GenerateNewTokensResponseDto;
import com.housing.back.dto.response.auth.IdCheckResponseDto;
import com.housing.back.dto.response.auth.JwtResponseDto;
import com.housing.back.dto.response.auth.NicknameResponseDto;
import com.housing.back.dto.response.auth.SignInResponseDto;
import com.housing.back.dto.response.auth.SignUpResponseDto;
import com.housing.back.dto.response.auth.UserInfoResponseDto;
import com.housing.back.entity.auth.NickNameEntity;
import com.housing.back.entity.auth.RefreshTokenEntity;
import com.housing.back.entity.auth.UserEntity;
import com.housing.back.entity.auth.VerificationCodeEntity;
import com.housing.back.provider.EmailProvider;
import com.housing.back.provider.JwtProvider;
import com.housing.back.repository.auth.CertificationRepository;
import com.housing.back.repository.auth.NicknameRepository;
import com.housing.back.repository.auth.RefreshTokenRepository;
import com.housing.back.repository.auth.UserRepository;
import com.housing.back.repository.musical.MusicalLikeRepository;
import com.housing.back.repository.review.ReviewCommentRepository;
import com.housing.back.repository.review.ReviewLikeRepository;
import com.housing.back.repository.review.ReviewRepository;
import com.housing.back.service.AuthService;
import com.housing.back.service.JwtBlacklistService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthServiceImplement implements AuthService {
    
    private final UserRepository userRepository;
    private final NicknameRepository nicknameRepository; 
    private final CertificationRepository certificationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final EmailProvider emailProvider;
    private final JwtBlacklistService  jwtBlacklistService;
    private final JwtUtils jwtUtils;
    // private final ReviewLikeRepository reviewLikeRepository;
    // private final MusicalLikeRepository musicalLikeRepository;
    // private final ReviewCommentRepository reviewCommentRepository;
    // private final ReviewRepository reviewRepository;

    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();


    @Override
    public ResponseEntity<? super IdCheckResponseDto> idCheck(IdCheckRequestDto dto) {
        
        try{
            String userId = dto.getId();
            boolean isExistId = userRepository.existsByUserId(userId);
            if (isExistId) return IdCheckResponseDto.duplicatedId();

        } catch (Exception exception){
            exception.printStackTrace();
            return ResponseDto.databaseError();
        }

        return IdCheckResponseDto.success();

    }

    @Override
    public ResponseEntity<? super EmailCertificationResponseDto> emailCertification(EmailCertificationRequestDto dto) {
        String userId = dto.getId();
            String email = dto.getEmail();
            VerificationCodeEntity certificationEntity = certificationRepository.findByUserId(userId);
            if (certificationEntity != null) certificationRepository.deleteByUserId(userId);
            
        try {
            
            

            // boolean isExistId = userRepository.existsByUserId(userId);
            // if (isExistId) {
            //     // Delete any existing verification code for the user ID and email
                
            // }

            String certificationNumber = CertificationNumber.getCertificationNumber();

            boolean isSuccessed = emailProvider.sendCertificationMail(email, certificationNumber);
            if(!isSuccessed) return EmailCertificationResponseDto.mailSendFail();

            certificationEntity = new VerificationCodeEntity(userId, email, certificationNumber);
            certificationRepository.save(certificationEntity);
            
        } catch (Exception exception){
            exception.printStackTrace();
            return ResponseDto.databaseError();
        }

        return EmailCertificationResponseDto.success();

    }

    @Override
    public ResponseEntity<? super CheckCertificationResponseDto> checkCertification(CheckCertificationRequestDto dto) {
        
        try {

            String userId = dto.getId();
            String email = dto.getEmail();
            String certificationNumber = dto.getCertificationNumber();

            VerificationCodeEntity certificationEntity = certificationRepository.findByUserId(userId);
            if (certificationEntity == null) return CheckCertificationResponseDto.certificationFail();

            boolean isMatched = certificationEntity.getEmail().equals(email) && certificationEntity.getCode().equals(certificationNumber);
            if (!isMatched) return CheckCertificationResponseDto.certificationFail();
            
        } catch (Exception exception) {
            exception.printStackTrace();
            return ResponseDto.databaseError();
        }

        return CheckCertificationResponseDto.success();

    }

    @Override
    public ResponseEntity<? super SignUpResponseDto> signUp(SignUpRequestDto dto) {
        try {
            
            String userId = dto.getId();

            boolean isExistId = userRepository.existsByUserId(userId);
            if (isExistId) return SignUpResponseDto.duplicateId();

            String email = dto.getEmail();
            String certificationNumber = dto.getCertificationNumber();

            VerificationCodeEntity certificationEntity = certificationRepository.findByUserId(userId);
            boolean isMatched = 
                certificationEntity.getEmail().equals(email) && 
                certificationEntity.getCode().equals(certificationNumber);
            if (!isMatched) return SignUpResponseDto.certificationFail();

            String password = dto.getPassword();
            String encodedPassword = passwordEncoder.encode(password);
            dto.setPassword(encodedPassword);

            UserEntity userEntity = new UserEntity(dto);
            userRepository.save(userEntity);

            certificationRepository.deleteByUserId(userId);
            
        } catch (Exception exception) {
            exception.printStackTrace();
            return ResponseDto.databaseError();
        }

        return SignUpResponseDto.success();
    }

    @Override
    public ResponseEntity<? super SignInResponseDto> signIn(SignInRequestDto dto,HttpServletRequest request) {
        
        String accessToken = null;
        String refreshToken = null;
        Date accessTokenExpirationDate = null;
        Date refreshTokenExpirationDate = null;
        
        try {

            String ipAddress = IpUtils.extractIpAddress(request);
            dto.setIpAddress(ipAddress);

            String userId = dto.getId();

            Optional<UserEntity> userEntityOptional  = userRepository.findByUserId(userId);
            if(userEntityOptional  == null) return SignInResponseDto.signInFail();

            UserEntity userEntity = userEntityOptional.get();

            String password = dto.getPassword(); 
            String encodedPassword = userEntity.getPassword();
            boolean isMatched = passwordEncoder.matches(password, encodedPassword);
            if (!isMatched) return SignInResponseDto.signInFail();

            JwtResponseDto accessTokenData = jwtProvider.createAccessToken(userId);
            accessToken = accessTokenData.getToken();
            accessTokenExpirationDate = accessTokenData.getExpirationDate();

            JwtResponseDto refreshTokenData = jwtProvider.createRefreshToken(userId);
            refreshToken = refreshTokenData.getToken();
            refreshTokenExpirationDate = refreshTokenData.getExpirationDate();

            refreshTokenRepository.deleteExpiredTokens(userEntity, dto.getDeviceInfo(), dto.getIpAddress(), refreshTokenExpirationDate);

            RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
            .user(userEntity)
            .token(refreshToken)
            .deviceInfo(dto.getDeviceInfo())
            .ipAddress(dto.getIpAddress())
            .expiryDate(refreshTokenExpirationDate)
            .build();

            refreshTokenRepository.save(refreshTokenEntity);

        } catch (Exception exception) {
            exception.printStackTrace();
            return ResponseDto.databaseError();
        }

        return SignInResponseDto.success(accessToken, refreshToken, accessTokenExpirationDate.getTime() ,refreshTokenExpirationDate.getTime() );
    }

    @Override
    public ResponseEntity<ResponseDto> accessSecureArea(HttpServletRequest request) {
    
        String token = request.getHeader("Authorization").substring(7);
        String userId = jwtUtils.extractUserId(token);
        try {
            userRepository.findByUserId(userId);    
        } catch (Exception exception) {
            exception.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(ResponseCode.DATABASE_ERROR, ResponseMessage.DATABASE_ERROR));
        }
       
        return  ResponseEntity.status(HttpStatus.OK).body(new ResponseDto(ResponseCode.SUCCESS,ResponseMessage.SUCCESS));
    }

    @Override
    public ResponseEntity<? super NicknameResponseDto> checkNickName(NicknameRequestDto dto) {
        try {
            String nickname = dto.getNickname();
            boolean isExistNickname = nicknameRepository.existsByNickname(nickname);
            if (isExistNickname) return NicknameResponseDto.duplicatedNickname();
        } catch (Exception exception) {
            exception.printStackTrace();
            return ResponseDto.databaseError();
        }
        return NicknameResponseDto.success(dto.getNickname());
    }

    @Transactional
    @Override
    public ResponseEntity<? super NicknameResponseDto> createNickName(String authorizationHeader, NicknameRequestDto requestBody) {
        // 토큰에서 사용자 ID 추출
        String token = authorizationHeader.substring(7);
        String userId = jwtUtils.extractUserId(token);

        Optional<UserEntity> userEntityOptional  = userRepository.findByUserId(userId);
        // todo: useEntityOptional 없을때 예외처리해야댐
        if (!userRepository.existsByUserId(userId)) {
            return ResponseDto.userNotFound(); // 사용자가 존재하지 않음
        }
        UserEntity userEntity = userEntityOptional.get();
        try {
            // 닉네임 엔티티 생성 및 저장
            
            NickNameEntity nicknameEntity = nicknameRepository.findByUser(userEntity).orElse(new NickNameEntity());
            nicknameEntity.setUser(userEntity);
            nicknameEntity.setNickname(requestBody.getNickname());
            nicknameRepository.save(nicknameEntity);

        } catch (Exception e) {
            // 예외 발생 시 데이터베이스 오류 응답 반환
            e.printStackTrace();
            return ResponseDto.databaseError();
        }

        // 응답 DTO 생성 및 반환
        return NicknameResponseDto.success(requestBody.getNickname());
    }

    @Transactional
    @Override
    public ResponseEntity<? super NicknameResponseDto> findNickName(String authorizationHeader) {
        String token = authorizationHeader.substring(7);
        String userId = jwtUtils.extractUserId(token);

        if (!userRepository.existsByUserId(userId)) {
            return ResponseDto.userNotFound(); // 사용자가 존재하지 않음
        }

        // todo: useEntityOptional 없을때 예외처리해야댐
        Optional<UserEntity> userEntityOptional= userRepository.findByUserId(userId);
        UserEntity userEntity = userEntityOptional.get();

        Optional<NickNameEntity> nicknameEntityOptional = nicknameRepository.findByUser(userEntity);

        if (nicknameEntityOptional.isPresent()) {
            NickNameEntity nicknameEntity = nicknameEntityOptional.get();
            return NicknameResponseDto.success(nicknameEntity.getNickname());
        } else {
            return NicknameResponseDto.noNickname();
        }
    }

    
    
    
    @Transactional
    public ResponseEntity<ResponseDto> logout(String accessTokenHeader, String refreshTokenHeader) {
        String accessToken = null;
        String refreshToken = null;

        if (accessTokenHeader != null && accessTokenHeader.startsWith("Bearer ")) {
            accessToken = accessTokenHeader.substring(7);
        }

        if (refreshTokenHeader != null) {
            refreshToken = refreshTokenHeader;
        }

        if (accessToken == null && refreshToken == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ResponseDto(ResponseCode.VALIDATION_FAIL,ResponseMessage.VALIDATION_FAIL));
        }

        try {
            if (accessToken != null) { // 엑세스토큰이 있을경우
                long accessExpiration = jwtProvider.getExpiration(accessToken);
                jwtBlacklistService.addToBlacklist(accessToken, accessExpiration);
            }
    
            if (refreshToken != null) { // 리프레시토큰이 있을 경우
                long refreshExpiration = jwtProvider.getExpiration(refreshToken);
                jwtBlacklistService.addToBlacklist(refreshToken, refreshExpiration);
                // refreshTokenRepository.deleteByRefreshToken(refreshToken);
            }
    
        } catch (Exception exception) {
            exception.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ResponseDto(ResponseCode.DATABASE_ERROR,ResponseMessage.DATABASE_ERROR));
        }
        return ResponseEntity.status(HttpStatus.OK).body(new ResponseDto(ResponseCode.SUCCESS,ResponseMessage.SUCCESS));
    }
    
    @Override
    @Transactional
    public ResponseEntity<?> handleTokenRefresh(String authorizationHeader, Map<String, String> requestBody, HttpServletRequest request) {
    
        String refreshToken = authorizationHeader.replace("Bearer ", "");
        String deviceInfo = requestBody.get("deviceInfo");
        String ipAddress = IpUtils.extractIpAddress(request);

        boolean isTokenValid = verifyRefreshToken(refreshToken, deviceInfo, ipAddress);

        if (isTokenValid) {
            ResponseEntity<? super GenerateNewTokensResponseDto> response = generateNewTokens(refreshToken);
            return ResponseEntity.ok(response.getBody());
        } else {
            return GenerateNewTokensResponseDto.refreshTokenFail();
        }
    }

    @Override
    public boolean verifyRefreshToken(String refreshToken, String deviceInfo, String ipAddress) {
        RefreshTokenEntity tokenEntity = refreshTokenRepository.findByToken(refreshToken);
        if (tokenEntity == null) {
            System.out.println("리프레시 토큰이 데이터베이스에 존재하지 않습니다.");
            return false;
        }

        boolean isDeviceInfoMatch = tokenEntity.getDeviceInfo().equals(deviceInfo);
        boolean isIpAddressMatch = tokenEntity.getIpAddress().equals(ipAddress);
        boolean isTokenNotExpired = !tokenEntity.getExpiryDate().before(new Date());

        System.out.println("디바이스 정보 일치: " + isDeviceInfoMatch);
        System.out.println("IP 주소 일치: " + isIpAddressMatch);
        System.out.println("토큰 만료 여부: " + isTokenNotExpired);

        return isDeviceInfoMatch && isIpAddressMatch && isTokenNotExpired;
        // return true;
    }
    @Override
    public ResponseEntity<? super GenerateNewTokensResponseDto> generateNewTokens(String refreshToken) {

        RefreshTokenEntity refreshTokenEntity = refreshTokenRepository.findByToken(refreshToken);

        if(refreshTokenEntity != null){
            String userId = jwtProvider.validate(refreshToken);

            JwtResponseDto accessTokenData = jwtProvider.createAccessToken(userId);
            String newAccessToken = accessTokenData.getToken();
            Date accessTokenExpirationDate = accessTokenData.getExpirationDate();

            JwtResponseDto refreshTokenData = jwtProvider.createRefreshToken(userId);
            String newRefreshToken = refreshTokenData.getToken();
            Date newRefreshTokenExpirationDate = refreshTokenData.getExpirationDate();

            RefreshTokenEntity tokenEntity = refreshTokenRepository.findByToken(refreshToken);
            tokenEntity.setToken(newRefreshToken);
            tokenEntity.setExpiryDate(newRefreshTokenExpirationDate);
            refreshTokenRepository.save(tokenEntity);
            
            return GenerateNewTokensResponseDto.success(
                newAccessToken,
                newRefreshToken,
                accessTokenExpirationDate.getTime(),
                newRefreshTokenExpirationDate.getTime()
            );

        } else {
            return GenerateNewTokensResponseDto.refreshTokenFail();
        }
       
        
    }

    @Override
    @Transactional
    public ResponseEntity<?> processDeviceInfo(String accessToken, String refreshToken, String deviceInfo, HttpServletRequest request) {
        String ipAddress = IpUtils.extractIpAddress(request);
        String decodedDeviceInfo = URLDecoder.decode(deviceInfo, StandardCharsets.UTF_8);
        String userId = jwtProvider.validate(accessToken.replace("Bearer ", ""));
        Date refreshTokenExpirationDate = new Date(jwtProvider.getExpiration(refreshToken));
        
        Optional<UserEntity> existingUser = userRepository.findByUserId(userId);
        UserEntity userEntity;

        if (existingUser.isPresent()) {
            userEntity = existingUser.get();
        } else {
            // 새로운 사용자 생성
            userEntity = new UserEntity(userId, "default-email@example.com", "naver");
            userRepository.save(userEntity);
        }

        try {
            int deletedTokensCount = refreshTokenRepository.deleteExpiredTokens(userEntity, decodedDeviceInfo, ipAddress, refreshTokenExpirationDate);
            System.out.println("@@@@Deleted tokens: " + deletedTokensCount);    
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to delete expired tokens");
        }
        
        try {
            System.out.println("Attempting to save refreshToken for userId: " + userId);
            
            RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
                    .user(userEntity)
                    .token(refreshToken)
                    .deviceInfo(decodedDeviceInfo)
                    .ipAddress(ipAddress)
                    .expiryDate(refreshTokenExpirationDate)
                    .build();

            refreshTokenRepository.save(refreshTokenEntity);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to save refreshToken");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", accessToken);
        response.put("refreshToken", refreshToken);
        response.put("deviceInfo", decodedDeviceInfo);
        response.put("ipAddress", ipAddress);

        return ResponseEntity.ok(response);
    }

    @Transactional
    public ResponseEntity<TestResponseDto> deleteUserByNickname(HttpServletRequest request, Map<String, String> requestBody) {
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return TestResponseDto.unAuthorized();
        }

        token = token.substring(7);
        String userId = jwtUtils.extractUserId(token);

        Optional<UserEntity> optionalUser = userRepository.findByUserId(userId);
        if (!optionalUser.isPresent()) {
            return TestResponseDto.userNotFound();
        }
        UserEntity user = optionalUser.get();

        String nickname = requestBody.get("nickname");
        if (nickname == null) {
            return TestResponseDto.validationFail();
        }

        Optional<NickNameEntity> optionalNickname = nicknameRepository.findByNickname(nickname);
        if (!optionalNickname.isPresent() || !optionalNickname.get().getUser().equals(user)) {
            return TestResponseDto.customValidationFail("닉네임이 일치하지 않습니다.");
        }

        try {
            // 사용자 삭제 - 연관된 모든 데이터는 자동으로 삭제됨 (ON DELETE CASCADE)
            userRepository.delete(user);

            return TestResponseDto.success();
        } catch (DataAccessException e) {
            return TestResponseDto.databaseError();
        } catch (Exception e) {
            return TestResponseDto.databaseError();
        }
        
    }
    
    @Override
    @Transactional
    public ResponseEntity<TestResponseDto> changePassword(String authorizationHeader, Map<String, String> requestBody) {
        String token = authorizationHeader.substring(7);
        String userId = jwtUtils.extractUserId(token);

        Optional<UserEntity> optionalUser = userRepository.findByUserId(userId);
        if (!optionalUser.isPresent()) {
            return TestResponseDto.userNotFound();
        }
        UserEntity user = optionalUser.get();

        String newPassword = requestBody.get("newPassword");
        if (newPassword == null || newPassword.isEmpty()) {
            return TestResponseDto.validationFail();
        }

        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);

        userRepository.save(user);

        return TestResponseDto.success();
    }

    @Override
    @Transactional
    public ResponseEntity<TestResponseDto> getUserInfo(String authorizationHeader) {
        String token = authorizationHeader.substring(7);
        String userId = jwtUtils.extractUserId(token);

        Optional<UserEntity> optionalUser = userRepository.findByUserId(userId);
        if (!optionalUser.isPresent()) {
            return TestResponseDto.userNotFound();
        }
        UserEntity user = optionalUser.get();

        Optional<NickNameEntity> optionalNickname = nicknameRepository.findByUser(user);
        if (!optionalNickname.isPresent()) {
            return TestResponseDto.customValidationFail("닉네임이 존재하지 않습니다.");
        }
        String nickname = optionalNickname.get().getNickname();

         UserInfoResponseDto userInfoResponseDto = new UserInfoResponseDto(
                user.getType(),
                user.getUserId(),
                user.getEmail(),
                nickname
        );

        return TestResponseDto.success(userInfoResponseDto);
    }
}
