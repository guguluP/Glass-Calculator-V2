# 🚂 RailReserve — Railway Reservation System

**JavaFX + Heavy CSS** desktop application built as a modern practice project in the Coding Practice workspace.

A fully functional, visually polished train ticket booking system with interactive seat maps, PNR generation, persistence via JSON, search, bookings, and admin management.

## ✨ Features (MVP v1)

- **Search Trains** by source/destination + date
- **Beautiful train cards** with availability pills (heavy glassmorphism CSS)
- **Interactive booking dialog**:
  - Class selector (SL / 3A / 2A / 1A) with live styling
  - Passenger count selector (1–6)
  - **Visual seat map** (40 seats) — click to select exactly the required number (CSS powered hover/selected states)
  - Dynamic passenger details form (name, age, gender, berth preference)
  - Live fare calculation with class multipliers
- **PNR generation** and beautiful confirmation screen with big styled PNR display
- **My Bookings** tab with list of all bookings (filtered by current user), cancel functionality (restores seats)
- **Manage Trains** admin tab — add/update custom trains + reload sample data
- **Change User** — simple session name for demo multi-user feel
- **Full persistence**: trains + bookings saved to `~/.railway-reservation/` (JSON, survives restarts)
- **Heavy CSS**: 200+ lines of custom rules — navy + saffron IRCTC-inspired theme, frosted cards, animated buttons, realistic seat grid, focus/hover/pressed states, modern inputs
- **Real Timetable Data (Phase 2)**: 28+ authentic trains from the Delhi–Howrah/Patna corridor (Rajdhani, Poorva, Mahabodhi, Vande Bharat, Duronto, Garib Rath, Humsafar, etc.) with real numbers, names, frequencies (Daily / Tu.W.F.Su etc.) and rich seat maps. Search "New Delhi" → "Howrah" now feels like the real IRCTC experience (times marked TBD until full schedules provided).
- **Payment Gateway (Phase 3)**: Full simulated IRCTC-style payment with Card / UPI / Net Banking / Wallet. Test cards: `4242...` = success, `4000...` = failure. Payment details are stored and shown in My Bookings + beautiful receipt screen.
- No external DB or server required

## 🛠 Tech Stack

- Java 21
- JavaFX 21 (pure code, no FXML)
- Jackson 2.17 (JSON)
- Maven + javafx-maven-plugin + shade plugin

## ▶️ How to Run

```bash
cd RailwayReservationSystem

# First time (downloads JavaFX modules)
mvn clean javafx:run

# Subsequent runs
mvn javafx:run
```

After first successful run, data is created in:
`~/.railway-reservation/trains.json` and `bookings.json`

## 📦 Build Fat Jar (optional)

```bash
mvn clean package
java -jar target/railway-reservation-system-1.0.jar
```

## 📁 Project Structure

```
RailwayReservationSystem/
├── pom.xml
├── .gitignore
├── README.md
└── src/main/
    ├── java/com/railwayreservation/
    │   ├── RailwayApp.java          # Main UI + logic
    │   ├── model/
    │   │   ├── Train.java
    │   │   ├── Booking.java
    │   │   └── Passenger.java
    │   ├── service/
    │   │   └── DataService.java     # JSON + business rules + 8 sample trains
    │   └── util/
    │       └── PNRGenerator.java
    └── resources/
        └── styles/
            └── railway-reservation.css   # HEAVY custom styling
```

## 🎨 CSS Highlights

- Deep navy background (`#0a192f`) + saffron accent (`#ff9933`)
- Glassmorphism cards with layered gradients + heavy dropshadows
- Fully interactive seat grid with distinct available/selected/booked states + scale animations
- Press/hover scale effects on all primary actions
- Monospace PNR hero display
- Tab, form, list, and dialog polish

## 🧪 Sample / Real Data

**Phase 2**: 28+ real trains from official extracted timetables (Delhi–Eastern India corridor):
- Rajdhani (12301/12302, 12306, 12310, 12314...), Poorva, Mahabodhi (12397/12398), Vande Bharat (22436), Duronto, Garib Rath, Humsafar, Vibhuti, Magadh, Vikramshila, Shatabdi, etc.
- Authentic frequencies (Daily, M.Th.Sa, Tu.W.F.Su...)
- Rich per-class availability
- Full station list for the corridor (New Delhi, Anand Vihar (T), Pt. Deen Dayal Upadhyaya Jn, Gaya, Howrah, Sealdah...)

Fallback to 8 generic samples only if the real JSON is missing.

## ✅ Verification Checklist (after `mvn clean javafx:run`)

1. App launches with beautiful dark UI and header
2. Search Delhi → Mumbai → several train cards appear
3. Open Book → pick class, passengers=2, select exactly 2 seats in grid, fill details → fare updates live
4. Confirm → PNR shown in styled ticket dialog, data saved
5. Go to My Bookings → see entry + Cancel works (seats restored)
6. Restart app → previous bookings & seat counts persist
7. Admin tab → add a new train → it appears in search
8. Book a ticket → "Proceed to Pay" opens full payment gateway (Card/UPI/NetBanking/Wallet)
9. Use test card `4242 4242 4242 4242` → success + receipt + PNR. Use `4000...` to test failure.
10. My Bookings now shows payment method + transaction ID
11. `mvn clean compile` succeeds cleanly

## 🔮 Future Enhancements (out of v1 scope)

- Real authentication + BCrypt
- MySQL persistence (reuse patterns from GlassCalculator)
- FXML + controllers
- QR code on ticket + PDF export
- Live availability across users

---

Built with ❤️ for coding practice — demonstrating strong JavaFX UI skills + heavy CSS craftsmanship.

**Version**: 1.0 • 2026
