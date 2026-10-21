package com.kmgtechnologies.ecommerce.repository;

import com.kmgtechnologies.ecommerce.entity.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void save_persistsCategoryWithGeneratedId() {
        Category saved = categoryRepository.saveAndFlush(new Category("Electronics"));
        entityManager.clear();

        assertThat(saved.getId()).isNotNull();
        assertThat(categoryRepository.findById(saved.getId()))
                .get()
                .extracting(Category::getName)
                .isEqualTo("Electronics");
    }

    @Test
    void save_rejectsDuplicateName() {
        categoryRepository.saveAndFlush(new Category("Electronics"));

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(new Category("Electronics")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_rejectsNullName() {
        assertThatThrownBy(() -> categoryRepository.saveAndFlush(new Category(null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_rejectsNameLongerThan100Characters() {
        String tooLong = "a".repeat(101);

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(new Category(tooLong)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}