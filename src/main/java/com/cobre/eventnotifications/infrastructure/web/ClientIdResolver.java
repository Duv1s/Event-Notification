package com.cobre.eventnotifications.infrastructure.web;

import com.cobre.eventnotifications.domain.ClientId;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the calling client's tenant from the request. Abstracted so the source can change without
 * touching the controllers (which must never read the client id from a query parameter).
 */
public interface ClientIdResolver {

    ClientId resolve(HttpServletRequest request);
}
