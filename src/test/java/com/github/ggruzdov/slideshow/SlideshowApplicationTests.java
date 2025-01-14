package com.github.ggruzdov.slideshow;

import com.github.ggruzdov.slideshow.model.Image;
import com.github.ggruzdov.slideshow.model.SlideShow;
import com.github.ggruzdov.slideshow.response.AddImageResponse;
import com.github.ggruzdov.slideshow.response.AddSlideShowResponse;
import com.github.ggruzdov.slideshow.response.ImageDetailsResponse;
import com.github.ggruzdov.slideshow.response.OrderedSlideShowDetailsResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SlideshowApplicationTests {

    // All the images are in the project root /images directory
    private static final String BASE_IMAGE_URL = "http://localhost:9000/images/";
    private static final String BEACH = BASE_IMAGE_URL + "beach.jpg";
    private static final String BIRDS = BASE_IMAGE_URL + "birds.jpg";
    private static final String BUTTERFLY = BASE_IMAGE_URL + "butterfly.jpg";
    private static final String MOUNTAIN_LAKE = BASE_IMAGE_URL + "mountain-lake.jpg";
    private static final String TREE = BASE_IMAGE_URL + "tree.jpg";

    @LocalServerPort
    private int port;

    @Autowired
    private RestClient restClient;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeAll
    static void setTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @SuppressWarnings("SqlWithoutWhere")
    @BeforeEach
    void cleanUpDB() {
        transactionTemplate.execute(tx -> {
            entityManager.createNativeQuery("DELETE FROM slide_shows_images").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM slide_shows").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM images").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM proofs_of_play").executeUpdate();
            return null;
        });
    }

    @Test
    void addImage() {
        // Given
        var mountainsImage =
            """
              {
                "url": "%s",
                "duration": 30
              }
            """.formatted(MOUNTAIN_LAKE);

        // When
        var result = restClient
            .post()
            .uri("http://localhost:%d/image".formatted(port))
            .contentType(MediaType.APPLICATION_JSON)
            .body(mountainsImage)
            .retrieve()
            .body(AddImageResponse.class);

        // Then
        assertNotNull(result);
        var image = entityManager.find(Image.class, result.id());
        assertNotNull(result);
        assertEquals("mountain-lake", image.getName());
    }

    @Test
    void addSlideShow() {
        // Given
        var imageList =
        """
          [
            {
              "url": "%s",
              "duration": 10
            },
            {
              "url": "%s",
              "duration": 15
            },
            {
              "url": "%s",
              "duration": 20
            }
          ]
        """.formatted(BEACH, BIRDS, BUTTERFLY);

        // When
        var result = restClient
            .post()
            .uri("http://localhost:%d/slideshow".formatted(port))
            .contentType(MediaType.APPLICATION_JSON)
            .body(imageList)
            .retrieve()
            .body(AddSlideShowResponse.class);

        // Then
        assertNotNull(result);
        var slideShow = entityManager.find(SlideShow.class, result.id());
        assertEquals(3, slideShow.getImages().size());
    }

    @Test
    void appendImage() {
        // Given
        var tree = persistImage(TREE);
        var slideShow = persistSlideShow();

        // When
        restClient
            .post()
            .uri("http://localhost:%d/slideshow/%d/append/%d".formatted(port, slideShow.getId(), tree.getId()))
            .retrieve()
            .toEntity(Void.class);

        // Then
        var result = entityManager.find(SlideShow.class, slideShow.getId());
        assertNotNull(result);
        assertEquals(4, result.getImages().size());
        //assertEquals("tree", result.getImages().);
    }

    @Test
    void getOrderedSlideShow() {
        // Given
        var tree = persistImage(TREE);
        var slideShow = persistSlideShow();

        // When(append image first to check addition order)
        restClient
            .post()
            .uri("http://localhost:%d/slideshow/%d/append/%d".formatted(port, slideShow.getId(), tree.getId()))
            .retrieve()
            .toEntity(Void.class);

        var result = restClient
            .get()
            .uri("http://localhost:%d/slideshow/%d/ordered".formatted(port, slideShow.getId()))
            .retrieve()
            .body(new ParameterizedTypeReference<OrderedSlideShowDetailsResponse>() {});

        // Then
        assertNotNull(result);
        assertEquals(4, result.images().size());
        assertEquals(BEACH, result.activeImage().url());
        assertEquals(TREE, result.images().getLast().url());
    }

    @Test
    void imageSearch() {
        // Given
        var mountainLake = persistImage(MOUNTAIN_LAKE);

        // When
        var result = restClient
            .get()
            .uri("http://localhost:%d/images/search?name=mountain-lake".formatted(port))
            .retrieve()
            .body(new ParameterizedTypeReference<List<ImageDetailsResponse>>() {});

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(mountainLake.getId(), result.getFirst().id());
        assertEquals(mountainLake.getUrl(), result.getFirst().url());
    }

    @Test
    void saveProofOfPlay() {
        // Given
        var persistedSlideShow = persistSlideShow();

        // When
        restClient
            .post()
            .uri("http://localhost:%d/slideshow/%d/proof-of-play/%d".formatted(port, persistedSlideShow.getId(), persistedSlideShow.getActiveImage().getId()))
            .retrieve()
            .toEntity(Void.class);

        // Then
        var result = entityManager.find(SlideShow.class, persistedSlideShow.getId());
        assertNotNull(result);
        assertEquals("birds", result.getActiveImage().getName());
    }

    @Test
    void deleteImage() {
        // Given
        var mountainLake = persistImage(MOUNTAIN_LAKE);

        // When
        restClient
            .delete()
            .uri("http://localhost:%d/image/%d".formatted(port, mountainLake.getId()))
            .retrieve()
            .toEntity(Void.class);

        // Then
        assertNull(entityManager.find(Image.class, mountainLake.getId()));
    }

    @Test
    void deleteImageWhichIsActiveImageInSlideShow() {
        // Given
        var slideShow = persistSlideShow();
        var activeImage = slideShow.getActiveImage();

        // When
        restClient
            .delete()
            .uri("http://localhost:%d/image/%d".formatted(port, activeImage.getId()))
            .retrieve()
            .toEntity(Void.class);

        // Then
        assertNull(entityManager.find(Image.class, activeImage.getId()));
        var result = entityManager.find(SlideShow.class, slideShow.getId());
        assertEquals("birds", result.getActiveImage().getName());
    }

    @Test
    void deleteSlideShow() {
        // Given
        var persistedSlideShow = persistSlideShow();
        var imageIds = persistedSlideShow.getImages().stream().map(Image::getId);

        // When
        restClient
            .delete()
            .uri("http://localhost:%d/slideshow/%d".formatted(port, persistedSlideShow.getId()))
            .retrieve()
            .toEntity(Void.class);

        // Then
        assertNull(entityManager.find(SlideShow.class, persistedSlideShow.getId()));
        // Ensure that all connected images still remain in DB
        imageIds.forEach(imageId -> {
            var image = entityManager
                .createQuery("SELECT i FROM Image i LEFT JOIN FETCH i.slideShows WHERE i.id = :id", Image.class)
                .setParameter("id", imageId)
                .getSingleResult();
            assertNotNull(image);
            assertTrue(image.getSlideShows().isEmpty());
        });
    }

    private Image persistImage(String url) {
        return transactionTemplate.execute(tx -> {
            var image = new Image(url, 30);
            entityManager.persist(image);
            return image;
        });
    }

    private SlideShow persistSlideShow() {
        return transactionTemplate.execute(tx -> {
            var slideShow = new SlideShow();
            slideShow.addImage(new Image(BEACH, 10));
            slideShow.addImage(new Image(BIRDS, 15));
            slideShow.addImage(new Image(BUTTERFLY, 20));
            slideShow.setActiveImage(slideShow.getImages().stream().findFirst().orElseThrow());
            entityManager.persist(slideShow);

            return slideShow;
        });
    }
}
