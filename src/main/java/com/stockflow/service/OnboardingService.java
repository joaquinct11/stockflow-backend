package com.stockflow.service;

import com.stockflow.dto.OnboardingProgresoDTO;

public interface OnboardingService {
    OnboardingProgresoDTO calcularProgreso(String tenantId);
}
