package com.bookstore.backend.service;

import com.bookstore.backend.cache.CacheNames;
import com.bookstore.backend.cache.DataCacheRegistry;
import com.bookstore.backend.entities.Publisher;
import com.bookstore.backend.repository.BookRepository;
import com.bookstore.backend.repository.PublisherRepository;
import com.bookstore.backend.utils.SlugUtil;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class PublisherService {

  private final PublisherRepository publisherRepository;
  private final BookRepository bookRepository;

  private final DataCacheRegistry cacheRegistry;

  public PublisherService(
      PublisherRepository publisherRepository,
      BookRepository bookRepository,
      DataCacheRegistry cacheRegistry) {
    this.publisherRepository = publisherRepository;
    this.bookRepository = bookRepository;
    this.cacheRegistry = cacheRegistry;
  }

  // lấy tất cả publishers
  public Page<Publisher> getAllPublishers(int page, int limit, String q) {
    Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("createdAt").descending());
    if (q != null && !q.isEmpty()) {
      return publisherRepository.findByNameContainingIgnoreCase(q, pageable);
    }
    return publisherRepository.findAll(pageable);
  }

  // lấy tất cả các publishers không phân trang
  public List<Publisher> getAllPublishers1() {
    return publisherRepository.findAll(Sort.by("createdAt").descending());
  }

  // lấy 1 publisher theo id
  public Optional<Publisher> getPublisherById(String id) {
    return publisherRepository.findById(id);
  }

  // tạo publisher
  public Publisher createPublisher(Publisher publisher) {
    if (publisherRepository.findByName(publisher.getName()).isPresent()) {
      throw new IllegalArgumentException("Tên nhà xuất bản đã tồn tại");
    }
    publisher.setSlug(SlugUtil.toSlug(publisher.getName()));
    return publisherRepository.save(publisher);
  }

  // cập nhật publisher
  public Publisher updatePublisher(String id, Publisher publisher) {
    return publisherRepository
        .findById(id)
        .map(
            existingPublisher -> {
              publisherRepository
                  .findByName(publisher.getName())
                  .filter(a -> !a.getId().equals(id))
                  .ifPresent(
                      a -> {
                        throw new IllegalArgumentException("Tên nhà xuất bản đã tồn tại");
                      });

              publisher.setSlug(SlugUtil.toSlug(publisher.getName()));
              existingPublisher.setName(publisher.getName());
              existingPublisher.setSlug(publisher.getSlug());
              Publisher saved = publisherRepository.save(existingPublisher);
              cacheRegistry.invalidate(CacheNames.BOOKS, CacheNames.IMPORT_RECEIPTS);
              return saved;
            })
        .orElseThrow(() -> new EntityNotFoundException("Nhà xuất bản không tìm thấy"));
  }

  // xóa publisher
  public void deletePublisher(String id) {
    Publisher publisher =
        publisherRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Nhà xuất bản không tìm thấy"));

    if (bookRepository.existsByPublisher(publisher)) {
      throw new IllegalStateException(
          "Nhà xuất bản này không thể xóa vì vẫn còn sách liên kết với họ");
    }

    publisherRepository.deleteById(id);
  }
}
