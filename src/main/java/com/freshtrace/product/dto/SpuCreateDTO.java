package com.freshtrace.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SpuCreateDTO {

    @NotNull(message = "所属品类不能为空")
    private Long categoryId;

    @NotBlank(message = "SPU名称不能为空")
    @Size(max = 100, message = "SPU名称长度不能超过 100")
    private String name;

    @Size(max = 100, message = "品种长度不能超过 100")
    private String variety;

    @Size(max = 100, message = "产地长度不能超过 100")
    private String origin;

    private String description;

    @Size(max = 500, message = "主图URL长度不能超过 500")
    private String mainImage;

    private List<String> tags;

    private Integer status;

    @Valid
    private List<SpuAttributeDTO> attributes;
}
