package com.stockflow.util;

import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.Set;

@Component
public class TokenBlacklist {

    private final Set<String> blacklistedTokens = new HashSet<>();

    public void addTokenToBlacklist(String token) {
        System.out.println("DEBUG: Agregando token a blacklist: " + token);
        System.out.println("DEBUG: Token length: " + token.length());
        blacklistedTokens.add(token);
        System.out.println("DEBUG: Blacklist size: " + blacklistedTokens.size());
    }

    public boolean isTokenBlacklisted(String token) {
        boolean isBlacklisted = blacklistedTokens.contains(token);
        System.out.println("DEBUG: Verificando token: " + token);
        System.out.println("DEBUG: ¿Está en blacklist?: " + isBlacklisted);
        System.out.println("DEBUG: Tokens en blacklist: " + blacklistedTokens.size());
        return isBlacklisted;
    }

    public void clearBlacklist() {
        blacklistedTokens.clear();
        System.out.println("DEBUG: Blacklist limpiada");
    }
}