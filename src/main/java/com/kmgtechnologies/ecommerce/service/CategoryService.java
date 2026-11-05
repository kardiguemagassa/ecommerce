package com.kmgtechnologies.ecommerce.service;

import com.kmgtechnologies.ecommerce.config.CacheNames;
import com.kmgtechnologies.ecommerce.dto.CategoryRequest;
import com.kmgtechnologies.ecommerce.dto.CategoryResponse;
import com.kmgtechnologies.ecommerce.entity.Category;
import com.kmgtechnologies.ecommerce.exception.DuplicateResourceException;
import com.kmgtechnologies.ecommerce.exception.ResourceInUseException;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.repository.CategoryRepository;
import com.kmgtechnologies.ecommerce.repository.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll(Sort.by("name")).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    public CategoryResponse findById(Long id) {
        return CategoryResponse.from(getCategory(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().strip();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw duplicate(name);
        }
        try {
            return CategoryResponse.from(categoryRepository.saveAndFlush(new Category(name)));
        } catch (DataIntegrityViolationException ex) {
            throw duplicate(name);
        }
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, allEntries = true)
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = getCategory(id);
        String name = request.name().strip();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw duplicate(name);
        }
        category.setName(name);
        try {
            categoryRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw duplicate(name);
        }
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long id) {
        Category category = getCategory(id);
        if (productRepository.existsByCategoryId(id)) {
            throw new ResourceInUseException("Category " + id + " still contains products");
        }
        categoryRepository.delete(category);
    }

    /**
     * Same error for both paths: the upfront existence check, and the unique index on lower(name)
     * that catches a concurrent duplicate. Flushing inside the method makes that violation surface
     * here, not later at commit as a generic integrity error.
     */
    private static DuplicateResourceException duplicate(String name) {
        return new DuplicateResourceException("Category '" + name + "' already exists");
    }

    private Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }
}