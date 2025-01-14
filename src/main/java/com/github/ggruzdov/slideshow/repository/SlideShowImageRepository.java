package com.github.ggruzdov.slideshow.repository;

import com.github.ggruzdov.slideshow.model.SlideShowImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SlideShowImageRepository extends JpaRepository<SlideShowImage, SlideShowImage.PK> {

    @Query(
        value = """
            with ssi_ordered as(
                select ssi.*, lead(ssi.image_id) over () as next_image_id
                from slide_shows_images ssi
                where ssi.slide_show_id = :slideShowId
                order by ssi.created_at
            )
            select * from slide_shows_images ssi2
            where ssi2.image_id = (select ssio.next_image_id from ssi_ordered ssio where ssio.image_id = :imageId)
           """,
        nativeQuery = true
    )
    Optional<SlideShowImage> getNext(Integer slideShowId, Long imageId);

    SlideShowImage findFirstByPkSlideShowIdOrderByCreatedAt(Integer slideShowId);
}