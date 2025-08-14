package tkitem.backend.domain.auth.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.member.vo.Member;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService{

	@Override
	public void logout(Member member) {

	}
}
