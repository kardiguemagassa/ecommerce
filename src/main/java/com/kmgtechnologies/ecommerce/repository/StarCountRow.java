package com.kmgtechnologies.ecommerce.repository;

/** How many visible reviews give a product this number of stars (a row of a GROUP BY). */
public record StarCountRow(Integer stars, Long count) {
}
