package com.fleetmanagement.api_rest.business.service;

import com.fleetmanagement.api_rest.persistence.entity.RoleEntity;
import com.fleetmanagement.api_rest.persistence.entity.RoleEnum;
import com.fleetmanagement.api_rest.persistence.entity.UserEntity;
import com.fleetmanagement.api_rest.persistence.repository.RoleRepository;
import com.fleetmanagement.api_rest.persistence.repository.UserRepository;
import com.fleetmanagement.api_rest.presentation.dto.AuthCreateUserRequest;
import com.fleetmanagement.api_rest.presentation.dto.AuthLoginRequest;
import com.fleetmanagement.api_rest.presentation.dto.AuthResponse;
import com.fleetmanagement.api_rest.presentation.dto.UserDTO;
import com.fleetmanagement.api_rest.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UserDetailService implements UserDetailsService {

	@Autowired
	UserRepository userRepository;
	@Autowired
	private JwtUtils jwtUtils;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private RoleRepository roleRepository;

	public AuthResponse loginUser(AuthLoginRequest authLoginRequest) {
		System.out.println("UserDetailService -> loginUser -> authLoginRequest " + authLoginRequest);
		String email = authLoginRequest.email();
		String password = authLoginRequest.password();

		Authentication authentication = this.authenticate(email, password);
		SecurityContextHolder.getContext().setAuthentication(authentication);

		String accessToken = jwtUtils.createToken(authentication);

		UserEntity userEntity = (UserEntity) authentication.getPrincipal();
		UserDTO userDTO = new UserDTO(userEntity.getId(), userEntity.getEmail());

		AuthResponse authResponse = new AuthResponse(accessToken, userDTO);

		return authResponse;
	}

	public Authentication authenticate(String email, String password) {
		System.out.println("UserDetailService -> authenticate -> email " + email);
		System.out.println("UserDetailService -> authenticate -> password " + password);
		UserDetails userDetails = this.loadUserByUsername(email);

		if (!passwordEncoder.matches(password, userDetails.getPassword())) {
			throw new BadCredentialsException("Incorrect Password");
		}

		UserEntity userEntity = userRepository.findByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

		return new UsernamePasswordAuthenticationToken(userEntity, null, userDetails.getAuthorities());
	}

	@Override
	public UserDetails loadUserByUsername(String email) {
		System.out.println("UserDetailService -> loadUserByUsername -> email " + email);
		UserEntity userEntity = userRepository.findByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

		List<SimpleGrantedAuthority> authorityList = new ArrayList<>();

		authorityList.add(new SimpleGrantedAuthority("ROLE_".concat(userEntity.getRole().getRoleEnum().name())));

		return new User(userEntity.getEmail(), userEntity.getPassword(), userEntity.isEnabled(),
				userEntity.isAccountNonExpired(), userEntity.isCredentialsNonExpired(),
				userEntity.isAccountNonLocked(),
				authorityList);
	}

	public AuthResponse createUser(AuthCreateUserRequest createRoleRequest) {

		String name = createRoleRequest.name();
		String email = createRoleRequest.email();
		String password = createRoleRequest.password();
		RoleEnum roleName = createRoleRequest.roleRequest().roleName();

		Optional<RoleEntity> roleEntity = roleRepository.findByRoleEnum(roleName);

		if (roleEntity.isEmpty()) {
			throw new IllegalArgumentException("The roles specified does not exist.");
		}

		// 1st parameter .username(username)
		UserEntity userEntity = UserEntity.builder()
				.name(name)
				.email(email)
				.password(passwordEncoder.encode(password))
				.role(roleEntity.orElse(null))
				.isEnabled(true)
				.accountNonLocked(true)
				.accountNonExpired(true)
				.credentialsNonExpired(true).build();

		UserEntity userSaved = userRepository.save(userEntity);

		ArrayList<SimpleGrantedAuthority> authorities = new ArrayList<>();

		roleEntity.ifPresent(
				role -> authorities.add(new SimpleGrantedAuthority("ROLE_".concat(role.getRoleEnum().name()))));

		SecurityContext securityContextHolder = SecurityContextHolder.getContext();

		Authentication authentication = new UsernamePasswordAuthenticationToken(userSaved, null, authorities);

		String accessToken = jwtUtils.createToken(authentication);

		UserDTO userDTO = new UserDTO(userEntity.getId(), userEntity.getEmail());

		AuthResponse authResponse = new AuthResponse(accessToken, userDTO);

		return authResponse;
	}
}
