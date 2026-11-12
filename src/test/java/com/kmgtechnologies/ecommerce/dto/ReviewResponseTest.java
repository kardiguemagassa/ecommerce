package com.kmgtechnologies.ecommerce.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewResponseTest {

    @Test
    void author_isTheFirstNameAndTheInitialOfTheLastOne_neverTheEmail() {
        assertThat(ReviewResponse.author("Alice Martin")).isEqualTo("Alice M.");
        assertThat(ReviewResponse.author("  Jean  Paul   de la  fontaine ")).isEqualTo("Jean F.");
        assertThat(ReviewResponse.author("Carol")).isEqualTo("Carol");
        assertThat(ReviewResponse.author(" ")).isEqualTo("Client");
        assertThat(ReviewResponse.author(null)).isEqualTo("Client");
    }
}
