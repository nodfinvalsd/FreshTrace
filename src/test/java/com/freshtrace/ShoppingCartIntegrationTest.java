package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.product.entity.Category;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.ProductAttribute;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.mapper.CategoryMapper;
import com.freshtrace.product.mapper.ProductAttributeMapper;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.mapper.SpuMapper;
import com.freshtrace.trade.dto.CartAddDTO;
import com.freshtrace.trade.dto.CartQuantityDTO;
import com.freshtrace.trade.dto.CartSelectedDTO;
import com.freshtrace.trade.entity.ShoppingCart;
import com.freshtrace.trade.mapper.ShoppingCartMapper;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ShoppingCartIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FarmerMapper farmerMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private SpuMapper spuMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductAttributeMapper productAttributeMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    private static final String SPEC_5 = "[{\"name\":\"规格\",\"value\":\"5斤装\"}]";
    private static final String SPEC_10 = "[{\"name\":\"规格\",\"value\":\"10斤装\"}]";

    @Test
    void addSuccess() throws Exception {
        long productId = createOnSaleProduct(new BigDecimal("20.00"), 100, Map.of());
        mockMvc.perform(post("/cart")
                        .header("Authorization", "Bearer " + userToken(900001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cartAdd(productId, 2, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.cartId").isNotEmpty())
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andExpect(jsonPath("$.data.selected").value(true));
    }

    @Test
    void addSameProductSameSpecAccumulates() throws Exception {
        long productId = createOnSaleProduct(new BigDecimal("20.00"), 100, Map.of());
        add(productId, 2, null, 900001L);
        add(productId, 3, null, 900001L);

        mockMvc.perform(get("/cart")
                        .header("Authorization", "Bearer " + userToken(900001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].quantity").value(5));
    }

    @Test
    void addSameProductDifferentSpecTwoRows() throws Exception {
        long productId = createOnSaleProduct(new BigDecimal("20.00"), 100,
                Map.of("5斤装", BigDecimal.ZERO, "10斤装", new BigDecimal("30.00")));
        add(productId, 1, SPEC_5, 900001L);
        add(productId, 1, SPEC_10, 900001L);

        mockMvc.perform(get("/cart")
                        .header("Authorization", "Bearer " + userToken(900001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void addQuantityInvalid() throws Exception {
        long productId = createOnSaleProduct(new BigDecimal("20.00"), 100, Map.of());
        mockMvc.perform(post("/cart")
                        .header("Authorization", "Bearer " + userToken(900001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cartAdd(productId, 0, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    @Test
    void addProductNotFound() throws Exception {
        mockMvc.perform(post("/cart")
                        .header("Authorization", "Bearer " + userToken(900001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cartAdd(999999L, 1, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30016));
    }

    @Test
    void addProductNotOnSale() throws Exception {
        long productId = createOnSaleProduct(new BigDecimal("20.00"), 100, Map.of());
        Product product = productMapper.selectById(productId);
        product.setLifecycle(4);
        productMapper.updateById(product);

        mockMvc.perform(post("/cart")
                        .header("Authorization", "Bearer " + userToken(900001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cartAdd(productId, 1, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30030));
    }

    @Test
    void addUnapprovedProductRejected() throws Exception {
        long productId = createOnSaleProduct(new BigDecimal("20.00"), 100, Map.of());
        Product product = productMapper.selectById(productId);
        product.setAuditStatus(0);
        productMapper.updateById(product);

        mockMvc.perform(post("/cart")
                        .header("Authorization", "Bearer " + userToken(900001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cartAdd(productId, 1, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30030));
    }

    @Test
    void addInvalidSpecRejected() throws Exception {
        long productId = createOnSaleProduct(new BigDecimal("20.00"), 100,
                Map.of("5斤装", BigDecimal.ZERO));
        mockMvc.perform(post("/cart")
                        .header("Authorization", "Bearer " + userToken(900001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cartAdd(productId, 1, "[{\"name\":\"规格\",\"value\":\"99斤装\"}]"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    @Test
    void usersIsolated() throws Exception {
        long productId = createOnSaleProduct(new BigDecimal("20.00"), 100, Map.of());
        add(productId, 1, null, 900001L);

        mockMvc.perform(get("/cart")
                        .header("Authorization", "Bearer " + userToken(900002L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void updateQuantitySuccess() throws Exception {
        long productId = createOnSaleProduct(new BigDecimal("20.00"), 100, Map.of());
        long cartId = add(productId, 2, null, 900001L);

        CartQuantityDTO dto = new CartQuantityDTO();
        dto.setQuantity(7);
        mockMvc.perform(put("/cart/{cartId}", cartId)
                        .header("Authorization", "Bearer " + userToken(900001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(7));
    }

    @Test
    void updateQuantityInvalid() throws Exception {
        long productId = createOnSaleProduct(new BigDecimal("20.00"), 100, Map.of());
        long cartId = add(productId, 2, null, 900001L);

        CartQuantityDTO dto = new CartQuantityDTO();
        dto.setQuantity(0);
        mockMvc.perform(put("/cart/{cartId}", cartId)
                        .header("Authorization", "Bearer " + userToken(900001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    @Test
    void updateQuantityNotFound() throws Exception {
        CartQuantityDTO dto = new CartQuantityDTO();
        dto.setQuantity(2);
        mockMvc.perform(put("/cart/{cartId}", 999999L)
                        .header("Authorization", "Bearer " + userToken(900001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30023));
    }

    @Test
    void updateQuantityOtherUserForbidden() throws Exception {
        long productId = createOnSaleProduct(new BigDecimal("20.00"), 100, Map.of());
        long cartId = add(productId, 2, null, 900001L);

        CartQuantityDTO dto = new CartQuantityDTO();
        dto.setQuantity(5);
        mockMvc.perform(put("/cart/{cartId}", cartId)
                        .header("Authorization", "Bearer " + userToken(900002L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30024));
    }

    @Test
    void selectAndUnselect() throws Exception {
        long productId = createOnSaleProduct(new BigDecimal("20.00"), 100, Map.of());
        long cartId = add(productId, 2, null, 900001L);

        CartSelectedDTO unselect = new CartSelectedDTO();
        unselect.setSelected(false);
        mockMvc.perform(put("/cart/{cartId}/selected", cartId)
                        .header("Authorization", "Bearer " + userToken(900001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(unselect)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selected").value(false));

        CartSelectedDTO select = new CartSelectedDTO();
        select.setSelected(true);
        mockMvc.perform(put("/cart/{cartId}/selected", cartId)
                        .header("Authorization", "Bearer " + userToken(900001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(select)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selected").value(true));
    }

    @Test
    void selectOtherUserCartForbidden() throws Exception {
        long productId = createOnSaleProduct(new BigDecimal("20.00"), 100, Map.of());
        long cartId = add(productId, 2, null, 900001L);

        CartSelectedDTO dto = new CartSelectedDTO();
        dto.setSelected(false);
        mockMvc.perform(put("/cart/{cartId}/selected", cartId)
                        .header("Authorization", "Bearer " + userToken(900002L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30024));
    }

    @Test
    void deleteSuccessWithLogicDelete() throws Exception {
        long productId = createOnSaleProduct(new BigDecimal("20.00"), 100, Map.of());
        long cartId = add(productId, 2, null, 900001L);

        mockMvc.perform(delete("/cart/{cartId}", cartId)
                        .header("Authorization", "Bearer " + userToken(900001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/cart")
                        .header("Authorization", "Bearer " + userToken(900001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        Integer deletedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_shopping_cart WHERE id = ? AND deleted = 1", Integer.class, cartId);
        assertThat(deletedRows).isEqualTo(1);
    }

    @Test
    void deleteOtherUserCartForbidden() throws Exception {
        long productId = createOnSaleProduct(new BigDecimal("20.00"), 100, Map.of());
        long cartId = add(productId, 2, null, 900001L);

        mockMvc.perform(delete("/cart/{cartId}", cartId)
                        .header("Authorization", "Bearer " + userToken(900002L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30024));
    }

    @Test
    void deleteNotFound() throws Exception {
        mockMvc.perform(delete("/cart/{cartId}", 999999L)
                        .header("Authorization", "Bearer " + userToken(900001L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30023));
    }

    @Test
    void readdAfterDeleteResetsQuantity() throws Exception {
        long productId = createOnSaleProduct(new BigDecimal("20.00"), 100, Map.of());
        long cartId = add(productId, 3, null, 900001L);

        mockMvc.perform(delete("/cart/{cartId}", cartId)
                        .header("Authorization", "Bearer " + userToken(900001L)))
                .andExpect(status().isOk());

        add(productId, 2, null, 900001L);
        List<ShoppingCart> rows = shoppingCartMapper.selectList(new LambdaQueryWrapper<ShoppingCart>()
                .eq(ShoppingCart::getUserId, 900001L));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getQuantity()).isEqualTo(2);
        assertThat(rows.get(0).getDeleted()).isZero();
    }

    @Test
    void listShowsCurrentPriceWithSpecExtraPrice() throws Exception {
        long productId = createOnSaleProduct(new BigDecimal("20.00"), 100,
                Map.of("5斤装", BigDecimal.ZERO, "10斤装", new BigDecimal("30.00")));
        add(productId, 2, SPEC_10, 900001L);

        mockMvc.perform(get("/cart")
                        .header("Authorization", "Bearer " + userToken(900001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].price").value(20.00))
                .andExpect(jsonPath("$.data[0].unitPrice").value(50.00))
                .andExpect(jsonPath("$.data[0].totalPrice").value(100.00))
                .andExpect(jsonPath("$.data[0].onSale").value(true))
                .andExpect(jsonPath("$.data[0].insufficientStock").value(false));
    }

    @Test
    void listInsufficientStockFlag() throws Exception {
        long productId = createOnSaleProduct(new BigDecimal("20.00"), 1, Map.of());
        add(productId, 2, null, 900001L);

        mockMvc.perform(get("/cart")
                        .header("Authorization", "Bearer " + userToken(900001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stock").value(1))
                .andExpect(jsonPath("$.data[0].insufficientStock").value(true));
    }

    @Test
    void listOffShelfProductFlag() throws Exception {
        long productId = createOnSaleProduct(new BigDecimal("20.00"), 100, Map.of());
        add(productId, 2, null, 900001L);

        Product product = productMapper.selectById(productId);
        product.setLifecycle(4);
        productMapper.updateById(product);

        mockMvc.perform(get("/cart")
                        .header("Authorization", "Bearer " + userToken(900001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].onSale").value(false))
                .andExpect(jsonPath("$.data[0].productExists").value(true));
    }

    @Test
    void updateQuantityAllowedWhenProductOffShelf() throws Exception {
        long productId = createOnSaleProduct(new BigDecimal("20.00"), 100, Map.of());
        long cartId = add(productId, 2, null, 900001L);

        Product product = productMapper.selectById(productId);
        product.setLifecycle(4);
        productMapper.updateById(product);

        CartQuantityDTO dto = new CartQuantityDTO();
        dto.setQuantity(3);
        mockMvc.perform(put("/cart/{cartId}", cartId)
                        .header("Authorization", "Bearer " + userToken(900001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(3))
                .andExpect(jsonPath("$.data.onSale").value(false));
    }

    @Test
    void unauthenticatedRejected() throws Exception {
        mockMvc.perform(get("/cart"))
                .andExpect(status().isUnauthorized());
    }

    private long add(long productId, int quantity, String specSnapshot, long userId) throws Exception {
        mockMvc.perform(post("/cart")
                        .header("Authorization", "Bearer " + userToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cartAdd(productId, quantity, specSnapshot))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        ShoppingCart cart = shoppingCartMapper.selectOne(new LambdaQueryWrapper<ShoppingCart>()
                .eq(ShoppingCart::getUserId, userId)
                .eq(ShoppingCart::getProductId, productId)
                .eq(ShoppingCart::getSpecSnapshot, specSnapshot == null ? "" : specSnapshot));
        return cart.getId();
    }

    private CartAddDTO cartAdd(long productId, int quantity, String specSnapshot) {
        CartAddDTO dto = new CartAddDTO();
        dto.setProductId(productId);
        dto.setQuantity(quantity);
        dto.setSpecSnapshot(specSnapshot);
        return dto;
    }

    private long createOnSaleProduct(BigDecimal price, int stock, Map<String, BigDecimal> attrValues) {
        long n = seq.incrementAndGet();
        User user = new User();
        user.setUsername("farmer_" + n);
        user.setPasswordHash("x");
        user.setPhone("138" + String.format("%08d", n % 100000000));
        user.setRole(0);
        user.setStatus(1);
        userMapper.insert(user);

        Farmer farmer = new Farmer();
        farmer.setUserId(user.getId());
        farmer.setRealName("张三");
        farmer.setIdCard("encrypted");
        farmer.setOrchardName("测试果园");
        farmer.setOrchardProvince("广东省");
        farmer.setOrchardCity("深圳市");
        farmer.setOrchardDistrict("南山区");
        farmer.setOrchardAddress("某村1号");
        farmer.setAuditStatus(1);
        farmerMapper.insert(farmer);

        Category category = new Category();
        category.setName("芒果" + n);
        category.setSortOrder(0);
        categoryMapper.insert(category);

        Spu spu = new Spu();
        spu.setCategoryId(category.getId());
        spu.setName("海南芒果" + n);
        spu.setStatus(1);
        spuMapper.insert(spu);

        Product product = new Product();
        product.setSpuId(spu.getId());
        product.setFarmerId(farmer.getId());
        product.setTitle("海南芒果 " + n + " 斤");
        product.setPrice(price);
        product.setStock(stock);
        product.setUnit("斤");
        product.setLifecycle(3);
        product.setAuditStatus(1);
        product.setSalesCount(0);
        product.setViewCount(0);
        productMapper.insert(product);

        attrValues.forEach((value, extraPrice) -> {
            ProductAttribute attribute = new ProductAttribute();
            attribute.setProductId(product.getId());
            attribute.setAttrName("规格");
            attribute.setAttrValue(value);
            attribute.setExtraPrice(extraPrice);
            productAttributeMapper.insert(attribute);
        });
        return product.getId();
    }

    private String userToken(long userId) {
        return jwtUtils.generateAccessToken(userId, 0);
    }

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
