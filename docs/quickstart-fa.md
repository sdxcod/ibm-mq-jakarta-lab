# راه‌اندازی آزمایشگاه Jakarta Messaging برای IBM MQ

این پروژهٔ مستقل، ارتباط با IBM MQ با استفاده از **Jakarta Messaging با مدیریت دستی ارتباط و تراکنش‌ها** را نشان می‌دهد.

Spring Boot فقط برنامهٔ خط فرمان را اجرا می‌کند، تنظیمات را می‌خواند و کلاس‌ها را به هم متصل می‌کند. ایجاد و بستن `JMSContext`، ارسال و دریافت، `commit()` و `rollback()` به دست کد برنامه انجام می‌شود. `JmsTemplate`، `@JmsListener`، Spring JMS و مدیریت تراکنش Spring در این پروژه استفاده نشده‌اند.

## ۱. اتصال به MQ

تنظیمات پیش‌فرض:

| تنظیم                 | مقدار             |
|-----------------------|-------------------|
| Host / Port           | `localhost:1414`  |
| Queue manager         | `QM1`             |
| Channel               | `DEV.APP.SVRCONN` |
| کاربر برنامه          | `app`             |
| صف ورودی              | `DEV.LAB.IN`      |
| صف خروجی              | `DEV.LAB.OUT`     |
| صف demo و integration | `DEV.LAB.TEST`    |

Java 21 را انتخاب کنید:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
export LAB_MQ_PASSWORD_FILE="$PWD/.secrets/mqAppPassword"
./mvnw -B -ntp verify
./scripts/lab.sh demo
```

Maven Wrapper نسخهٔ Maven را دریافت می‌کند؛ نصب جداگانهٔ Maven لازم نیست. در Windows، مسیر Bash را با WSL2 و Docker Desktop integration اجرا کنید، یا پس از build از `java -jar` استفاده کنید.

اسکریپت اول رمز تصادفی محلی می‌سازد و در اجرای بعدی همان رمزها را نگه می‌دارد. این فایل‌ها وارد Git یا Docker image برنامه نمی‌شوند. تنظیم `LICENSE=accept` مربوط به شرایط استفادهٔ IBM MQ Developer است؛ شرایط IBM را قبل از اجرای ایمیج بخوانید.

دستور demo سه نتیجهٔ `PASS` چاپ می‌کند: لغو ارسال، ارسال قطعی و بازگشت پیام دریافت‌شده با backout، و حذف قطعی با commit. پیام فارسی نیز برای آزمایش UTF-8 ارسال می‌شود. demo فقط پیام‌های خودش را با شناسهٔ MQ می‌خواند.

Demo سه نتیجهٔ `PASS:` دارد: لغو ارسال، ارسال قطعی متن فارسی و بازگشت پیام پس از rollback دریافت، و حذف قطعی پیام پس از commit دریافت. فقط شناسه‌های پیام‌های ساخته‌شده توسط همان demo را مصرف می‌کند و صف را پاک نمی‌کند.

## ۲. ارسال و دریافت ساده

```bash
./scripts/lab.sh send --lab.queue=in --lab.message='درود از Jakarta Messaging'
./scripts/lab.sh receive --lab.queue=in --lab.wait=3s
./scripts/lab.sh browse --lab.queue=in --lab.browse-limit=100
```

ارسال و دریافت مستقل، به‌صورت پیش‌فرض `commit` می‌شوند. برای لغو ارسال:

```bash
./scripts/lab.sh send --lab.queue=out --lab.message='این پیام منتشر نشود' --lab.outcome=rollback
```

برای دریافت دو بار یک پیام:

```bash
./scripts/lab.sh send --lab.queue=out --lab.message='این پیام دوباره دریافت شود'
./scripts/lab.sh receive --lab.queue=out --lab.outcome=backout
./scripts/lab.sh receive --lab.queue=out --lab.outcome=commit
```

`backout` و `rollback` هر دو به `JMSContext.rollback()` می‌رسند. `--lab.wait=0s` دریافت بدون انتظار است؛ کد از `receiveNoWait()` استفاده می‌کند، زیرا در استاندارد Jakarta، `receive(0)` به معنی انتظار نامحدود است. حداکثر انتظار این آزمایشگاه ۶۰ ثانیه است.

## ۳. آزمایش دستی تراکنش در shell

```bash
./scripts/lab.sh shell
```

سپس:

```text
connect
status
put in Hello Jakarta
put out Second pending message
commit
get in
rollback
get in
commit
browse out
get out
commit
disconnect
quit
```

در shell، ارسال یا دریافت تا قبل از `commit` یا `rollback` نهایی نمی‌شود. یک commit تمام عملیات معلق **همان context** را نهایی می‌کند، حتی اگر روی دو صف متفاوت باشند. خروج، EOF یا قطع اتصال، عملیات معلقِ شناخته‌شده را rollback می‌کند. بستن `JMSConsumer` پس از دریافت به معنی commit نیست؛ تراکنش متعلق به context است.

هر shell context خودش را دارد. برای کار هم‌زمان باید contextهای جداگانه بسازید؛ یک context را بین threadها به اشتراک نگذارید.

## ۴. IBM MQ Web Console: مشاهدهٔ صف‌های ورودی و خروجی

داشبورد همان Container قبلی قابل استفاده است. در مرورگر باز کنید:

[IBM MQ Web Console](https://localhost:9443/ibmmq/console)

ورود:

| مورد     | مقدار                                      |
|----------|--------------------------------------------|
| Username | `admin`                                    |
| Password | محتوای `.secrets/mqAdminPassword` در پروژه |

برای مشاهدهٔ رمز، از پوشهٔ پروژهٔ قبلی اجرا کنید:

```bash
cat .secrets/mqAdminPassword
```

رمز `admin` برای کنسول است؛ برنامه با کاربر `app` و فایل `mqAppPassword` متصل می‌شود. هنگام اتصال به MQ فعلی از رمزهای پروژهٔ جدید استفاده نکنید.

گواهی HTTPS محلی self-signed است؛ ممکن است مرورگر هشدار بدهد. برای همین کنسول محلی، استثناء گواهی را مطابق رابط مرورگر بپذیرید. این کار جایگزین تنظیم TLS معتبر برای محیط‌های دیگر نیست.

پس از ورود:

1. Queue Manager با نام `QM1` را باز کنید.
2. بخش **Queues** را پیدا کنید؛ اگر فیلتر دارد، `DEV.LAB` را جست‌وجو کنید.
3. صف‌های `DEV.LAB.IN`، `DEV.LAB.OUT` و `DEV.LAB.TEST` را ببینید.
4. در جزئیات صف، **Current depth** را مشاهده کنید. عنوان یا جای منوها ممکن است با نسخهٔ image متفاوت باشد.
5. پس از اجرای دستور، صفحه یا جدول را refresh کنید. اگر امکان Browse/View messages در نسخهٔ شما نمایش داده می‌شود، برای مشاهدهٔ پیام‌های موجود از همان گزینه استفاده کنید.

برای مشاهدهٔ اثر دستورها، وقتی صف آزمایشی مناسب دارید:

```bash
./scripts/lab.sh send --lab.queue=in --lab.message='Hello Web Console from Jakarta'
```

صف `DEV.LAB.IN` را refresh کنید. سپس:

```bash
./scripts/lab.sh receive --lab.queue=in --lab.wait=3s
```

و دوباره refresh کنید. در شرایط بدون فعالیت هم‌زمان، ارسالِ commit‌شده یک پیام اضافه و دریافتِ commit‌شده یک پیام حذف می‌کند. اگر پیام‌های دیگری در صف هستند، برای بررسی همان پیام از شناسهٔ چاپ‌شده در ارسال و `--lab.message-id` استفاده کنید.

برای دیدن rollback دریافت، ابتدا پیام را commit‌شده ارسال کنید، سپس در shell `get in` و `rollback` را اجرا کنید. بعد از rollback باید دریافت دوبارهٔ همان پیام ممکن باشد. عمق صف در زمان تراکنش باز به‌تنهایی نتیجهٔ قطعی ارسال یا دریافت را ثابت نمی‌کند؛ با عملیات دریافت و commit/rollback نیز بررسی کنید.

**کنسول وضعیت MQ را نمایش می‌دهد، نه تاریخچهٔ commandهای برنامهٔ جاوا.** خروجی ترمینال را کنار آن نگه دارید. لاگ‌های MQ را از پوشهٔ پروژه‌ای که broker را ساخته بررسی کنید:

```bash
docker compose logs -f mq
```

مشاهدهٔ مقدار عمق صف با MQSC، از همان پوشه:

```bash
docker compose exec -T mq runmqsc QM1 <<'MQSC'
DISPLAY QLOCAL('DEV.LAB.IN') CURDEPTH
DISPLAY QLOCAL('DEV.LAB.OUT') CURDEPTH
MQSC
```

مرجع رسمی: [تنظیمات developer image و Web Console](https://github.com/ibm-messaging/mq-container/blob/master/docs/developer-config.md).

## ۵. تفاوت browse با عمق صف

Jakarta Messaging استانداردی برای گرفتن عمق واقعی صف ندارد. دستور `browse` از `QueueBrowser` استفاده می‌کند و حداکثر ۱۰۰ پیام را به‌صورت پیش‌فرض می‌شمارد. `--lab.browse-limit` بین ۱ تا ۵۰۰۰ قابل تنظیم است. `observedMessages` تعداد مشاهده‌شده است؛ `limitReached=true` یعنی ممکن است پیام‌های بیشتری وجود داشته باشند.

برای سازگاری با دستورهای قبلی، `depth` نیز همان browse را اجرا می‌کند و **معادل Current depth سرور نیست**. مرور صف snapshot تضمین‌شده نیست و با مصرف‌کننده‌های هم‌زمان تغییر می‌کند. برای معیار عمق صف از Web Console یا MQSC استفاده کنید.

## ۶. شناسهٔ پیام و تبادل با پروژهٔ Native

در این پروژه خروجی شناسه معمولاً `ID:` به اضافهٔ ۴۸ کاراکتر hexadecimal است. پروژهٔ Native همان شناسهٔ MQ را بدون `ID:` چاپ می‌کند. این تفاوت نمایش است، نه تولید UUID جدید توسط برنامه.

```bash
./scripts/lab.sh receive --lab.queue=in --lab.message-id='ID:PUT_THE_48_HEX_HERE'
# شناسهٔ ۴۸ کاراکتری پروژهٔ Native نیز پذیرفته می‌شود.
```

برای اینکه دو پروژه پیام متنی یکدیگر را بخوانند، مقصد IBM با `WMQ_CLIENT_NONJMS_MQ` و CCSID=1208 تنظیم شده؛ بنابراین ارسال متنی این lab فاقد header نوع MQRFH2 است. ارسال و دریافت همچنان از Jakarta انجام می‌شود. حفظ تمام propertyهای اختصاصی JMS در این حالت هدف lab نیست.

در خروجی دریافت، `redelivered` و `deliveryCount` را ببینید. delivery count از ۱ شروع می‌شود؛ دقیقاً همان فیلد native `BackoutCount` نیست. تمرین تبادل در هر دو جهت در [exercises.md](exercises.md) آمده است.

## ۷. اجرای Container برنامه روی همان MQ قبلی

Compose پروژهٔ قبلی شبکهٔ `ibm-mq-native-lab_default` را می‌سازد. فایل `compose.existing.yaml` پروژهٔ جدید به آن شبکه متصل می‌شود و DNS سرویس `mq` را استفاده می‌کند:

```bash
export LAB_MQ_PASSWORD_FILE="$PWD/../ibm-mq-native-lab/.secrets/mqAppPassword"
docker compose -f compose.existing.yaml build agent
docker compose -f compose.existing.yaml run --rm agent
docker compose -f compose.existing.yaml run --rm agent --lab.command=shell
```

اگر نام پروژهٔ Compose را تغییر داده‌اید، شبکهٔ واقعی را با `docker network ls` پیدا کنید و قبل از اجرا تنظیم کنید:

```bash
export LAB_MQ_DOCKER_NETWORK='YOUR_EXISTING_NETWORK'
```

این روش broker یا volume تازه‌ای نمی‌سازد. برنامهٔ container خط فرمان است؛ بعد از demo خاتمه می‌یابد و پورت HTTP ندارد. برای اجرای آزمایش‌ها، همان شبکه و رمزهای broker کافی است.

## ۸. نصب مستقل، فقط در صورت نیاز

اگر MQ را از قبل استفاده نمی‌کنید و پورت‌های ۱۴۱۴ و ۹۴۴۳ آزادند:

```bash
unset LAB_MQ_PASSWORD_FILE
./scripts/init-lab.sh
./mvnw -B -ntp verify
docker compose up -d --wait --wait-timeout 360 mq
./scripts/lab.sh demo
```

Image و MQSC همان نسخهٔ پروژهٔ قبلی هستند. Image برای amd64 تنظیم شده؛ روی Apple Silicon به emulation نیاز دارد. مجوز IBM Advanced for Developers برای توسعهٔ محلی است؛ پیش از اجرا شرایط آن را بخوانید. رمزها به‌صورت فایل secrets mount می‌شوند. برنامه TLS جداگانه‌ای تنظیم نمی‌کند و listener روی loopback میزبان منتشر می‌شود.

برای توقف stack مستقل و حفظ داده‌ها، `docker compose down` کافی است. دستور `docker compose down -v` داده‌های همان stack را حذف می‌کند؛ فقط برای reset عمدی استفاده کنید. broker مشترک را از پوشهٔ پروژهٔ قبلی مدیریت کنید.

## ۹. تست‌ها و Kubernetes

```bash
./mvnw -B -ntp verify
./mvnw -B -ntp -Pmq-it verify
```

اولی تست‌های واحد را بدون سرور اجرا می‌کند. دومی به MQ آماده و رمز درست نیاز دارد. تست‌های integration فقط پیام‌های خودشان را با selector دریافت می‌کنند و صف را پاک نمی‌کنند. گزارش بررسی‌های انجام‌شده در [verification.md](verification.md) است.

اگر MQ پروژهٔ قبلی در Kubernetes نصب شده، فقط image و Job جدید را اضافه کنید:

```bash
docker build -t ibm-mq-jakarta-lab:0.1.0 .
kind load docker-image ibm-mq-jakarta-lab:0.1.0 --name mq-lab
kubectl apply -f k8s/demo-job.yaml
kubectl -n ibm-mq-lab wait --for=condition=complete job/jakarta-mq-demo --timeout=180s
kubectl -n ibm-mq-lab logs job/jakarta-mq-demo
```

Job از namespace و Secret قبلی استفاده می‌کند. برای minikube از `minikube image load` استفاده کنید. برای تکرار، فقط Job قبلی `jakarta-mq-demo` را حذف و دوباره apply کنید. retry خودکار Job صفر است. در cluster تازه، `scripts/k8s-up.sh` نصب اختیاری MQ را انجام می‌دهد و Secret موجود را حفظ می‌کند. namespace مشترک را بی‌دلیل حذف نکنید، چون پروژهٔ Native نیز از آن استفاده می‌کند.

Web Console در Kubernetes:

```bash
kubectl -n ibm-mq-lab port-forward svc/mq 9444:9443
```

سپس `https://localhost:9444/ibmmq/console` را با رمز admin همان نصب باز کنید.

## ۱۰. خطاهای رایج

| نشانه                            | بررسی                                                                            |
|----------------------------------|----------------------------------------------------------------------------------|
| فایل رمز پیدا نمی‌شود            | مقدار مطلق `LAB_MQ_PASSWORD_FILE`، پوشهٔ فعلی                                    |
| خطای ۲۰۳۵ / authentication       | رمز `app` متعلق به broker فعلی، channel و مجوزهای صف؛ امنیت را غیرفعال نکنید     |
| connection refused / ۲۵۳۸        | broker آماده است؟ Host/Port صحیح است؟ از داخل container نام `mq` را استفاده کنید |
| شبکهٔ external پیدا نمی‌شود      | `docker network ls` و `LAB_MQ_DOCKER_NETWORK`                                    |
| No message available             | پیام commit شده؟ selector درست است؟ مصرف‌کنندهٔ دیگری آن را برداشته؟             |
| UNKNOWN                          | نتیجهٔ عملیات/تراکنش مشخص نیست؛ disconnect و بررسی، بدون ارسال مجدد کورکورانه    |
| متن نامعتبر یا نوع غیرمتنی       | پیام با rollback بازمی‌گردد؛ حلقهٔ retry بی‌نهایت نسازید                         |
| کد تغییر کرده اما اجرا قدیمی است | `scripts/lab.sh` JAR موجود را استفاده می‌کند؛ دوباره `./mvnw verify` اجرا کنید   |