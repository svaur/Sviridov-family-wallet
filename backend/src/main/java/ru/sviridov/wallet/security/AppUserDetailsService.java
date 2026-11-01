package ru.sviridov.wallet.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ru.sviridov.wallet.domain.UserRepository;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .map(u -> new AppUser(u.getId(), u.getUsername(), u.getPasswordHash(), u.getPasswordChangedAt()))
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден: " + username));
    }
}
