package com.kmgtechnologies.ecommerce.service;

/**
 * Who the signed-in user is, as Keycloak says: the subject of the token (stable id, never reused), and the profile
 * claims copied into the customer record.
 *
 * @param subject       {@code sub} claim
 * @param email         {@code email} claim
 * @param emailVerified {@code email_verified} claim: an existing customer is linked by e-mail only if verified
 * @param fullName      {@code name} claim, or the e-mail when the user has no name
 */
public record CustomerIdentity(String subject, String email, boolean emailVerified, String fullName) {
}
