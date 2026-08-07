# Thiết lập VNPay cho FStu

## Chạy local sau khi pull

1. Chạy migration `courseqa-springboot-mvc2-skeleton/BACKEND/database/V20260805__vnpay_pro_subscriptions.sql` trên SQL Server.
2. Copy `.env.example` thành `.env`.
3. Điền `VNPAY_TMN_CODE` và `VNPAY_HASH_SECRET` Sandbox của bạn.
4. Giữ cấu hình local:

```properties
VNPAY_ENABLED=true
VNPAY_ENVIRONMENT=sandbox
VNPAY_PAYMENT_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=http://localhost:8080/api/payments/vnpay/return
VNPAY_IPN_URL=
FRONTEND_PAYMENT_RETURN_URL=http://localhost:5173/payment/result
VNPAY_ORDER_TTL_MINUTES=15
```

Khởi động Spring backend ở cổng `8080` và frontend ở cổng `5173`. Sandbox local không cần Cloudflare Tunnel: VNPay chuyển trình duyệt về Return URL localhost; backend chỉ settlement sau khi xác minh chữ ký HMAC, merchant, số tiền và mã giao dịch.

Nếu chưa có credential, giữ `VNPAY_ENABLED=false`; toàn bộ ứng dụng vẫn chạy, chỉ checkout VNPay bị tắt. Không commit `.env` hoặc HashSecret.

## Cách callback hoạt động

- Return và IPN dùng chung một settlement có khóa database.
- Callback nào đến trước sẽ xử lý đơn.
- Callback lặp trả trạng thái đã xác nhận và không tạo thêm subscription history.
- Tài khoản có gói trả phí còn hạn không thể gia hạn hoặc đổi sang gói khác.
- Khi hết hạn, người dùng mới có thể mua gói mới.

## IPN local tùy chọn

Để kiểm thử server-to-server IPN, dùng domain HTTPS ổn định (staging hoặc Cloudflare Named Tunnel) và cấu hình URL đó với merchant VNPay:

```properties
VNPAY_RETURN_URL=https://<stable-backend-domain>/api/payments/vnpay/return
VNPAY_IPN_URL=https://<stable-backend-domain>/api/payments/vnpay/ipn
```

Không dùng Quick Tunnel cho cấu hình chia sẻ vì hostname thay đổi sau mỗi lần chạy.

## Production

Production bắt buộc credential riêng, payment URL production và hai callback URL HTTPS công khai, ổn định. Backend sẽ fail-fast nếu production thiếu IPN, dùng localhost hoặc còn trỏ tới Sandbox.

## Kiểm tra

```powershell
cd courseqa-springboot-mvc2-skeleton\BACKEND
mvn test
```

Các trường hợp quan trọng: thanh toán thành công thành `PAID`; replay Return/IPN chỉ kích hoạt một lần; callback sai chữ ký/merchant/số tiền không kích hoạt; gói còn hạn khóa mọi giao dịch mua mới.
