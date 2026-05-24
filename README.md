# ✨ Glass Calculator V2

> A sleek, modern calculator application built with Java featuring a stunning glassmorphism design aesthetic.

<div align="center">

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![CSS](https://img.shields.io/badge/CSS3-1572B6?style=for-the-badge&logo=css3&logoColor=white)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)
![Status](https://img.shields.io/badge/Status-Active-brightgreen?style=for-the-badge)

</div>

---

## 📋 Table of Contents

- [About](#about)
- [Features](#features)
- [Getting Started](#getting-started)
- [Installation](#installation)
- [Usage](#usage)
- [Project Structure](#project-structure)
- [Technology Stack](#technology-stack)
- [Contributing](#contributing)
- [License](#license)

---

## 🎯 About

**Glass Calculator V2** is a modern desktop calculator application that combines elegant glassmorphism design with robust calculation functionality. The application features a transparent, frosted glass aesthetic that gives it a contemporary look while maintaining a clean, intuitive user interface.

This is the second iteration of the Glass Calculator project, bringing improved features, optimized performance, and an enhanced user experience.

---

## ✨ Features

- 🧮 **Full Arithmetic Operations** - Addition, subtraction, multiplication, and division
- 🎨 **Glassmorphism Design** - Modern transparent UI with blur effects
- ⌨️ **Intuitive Interface** - Clean, easy-to-use button layout
- 🔢 **Advanced Calculations** - Support for decimal operations and complex expressions
- 💾 **Calculation History** - Keep track of previous calculations
- 🎭 **Elegant Theme** - Frosted glass aesthetic with smooth animations
- 🖱️ **Responsive Controls** - Quick and responsive button feedback

---

## 🚀 Getting Started

### Prerequisites

Before you begin, ensure you have the following installed:
- **Java Development Kit (JDK)** 8 or higher
- **Maven** (optional, for build management)
- Any modern operating system (Windows, macOS, Linux)

### Installation

1. **Clone the Repository**
   ```bash
   git clone https://github.com/guguluP/Glass-Calculator-V2.git
   cd Glass-Calculator-V2
   ```

2. **Navigate to Project Directory**
   ```bash
   cd Project
   ```

3. **Compile the Project**
   ```bash
   javac *.java
   ```

4. **Run the Application**
   ```bash
   java Main
   ```

---

## 📖 Usage

1. **Launch the Application**
   - Run the compiled Java application

2. **Perform Calculations**
   - Click number buttons to enter values
   - Use operation buttons (+, -, ×, ÷) for calculations
   - Press the equals button (=) to get results

3. **Clear Operations**
   - Use the Clear button to reset the calculator
   - Backspace to delete the last digit

4. **View Results**
   - Results display in the elegant glass-themed output panel
   - Previous calculations are visible in the history (if available)

### Example Operations

| Operation | Steps |
|-----------|-------|
| Add 5 + 3 | Click 5 → + → 3 → = |
| Multiply 7 × 8 | Click 7 → × → 8 → = |
| Divide 20 ÷ 4 | Click 20 → ÷ → 4 → = |

---

## 📁 Project Structure

```
Glass-Calculator-V2/
├── Project/
│   ├── src/
│   │   ├── Main.java           # Application entry point
│   │   ├── Calculator.java     # Core calculator logic
│   │   └── ...
│   ├── css/
│   │   └── style.css           # Glassmorphism styling
│   └── ...
└── .gitignore
```

### Key Files

- **Main.java** - Entry point of the application and GUI initialization
- **Calculator.java** - Core calculation engine and business logic
- **style.css** - Glassmorphism CSS styling and visual theme

---

## 🛠️ Technology Stack

| Technology | Purpose |
|-----------|---------|
| **Java** | Core application logic and GUI framework |
| **Swing/AWT** | Desktop GUI components |
| **CSS** | Visual styling and glassmorphism effects |

---

## 🎨 Design Highlights

### Glassmorphism Aesthetic
- Frosted glass effect with transparency
- Subtle blur filters for depth
- Modern color palette with smooth gradients
- Responsive button states with hover effects

### User Experience
- Clear, legible display panel
- Well-organized button grid
- Intuitive operation flow
- Smooth animations and transitions

---

## 🤝 Contributing

We welcome contributions! Here's how to get involved:

1. **Fork the Repository**
   ```bash
   click the Fork button on GitHub
   ```

2. **Create a Feature Branch**
   ```bash
   git checkout -b feature/YourFeatureName
   ```

3. **Make Your Changes**
   ```bash
   git add .
   git commit -m "Add: Brief description of changes"
   ```

4. **Push to Your Branch**
   ```bash
   git push origin feature/YourFeatureName
   ```

5. **Open a Pull Request**
   - Describe your changes clearly
   - Reference any related issues

### Contribution Ideas
- 🧪 Add scientific calculator mode
- 🌙 Implement dark/light theme toggle
- 📱 Create a responsive mobile version
- 🔧 Optimize performance
- 📝 Improve documentation
- 🐛 Fix bugs and edge cases

---

## 📚 Code Examples

### Running the Calculator
```java
public class Main {
    public static void main(String[] args) {
        // Initialize the GUI
        CalculatorFrame frame = new CalculatorFrame();
        frame.setVisible(true);
    }
}
```

### Basic Calculation
```java
double result = calculator.add(5, 3);      // Returns 8
double result = calculator.multiply(7, 8); // Returns 56
```

---

## 🐛 Known Issues & TODO

- [ ] Add keyboard support for faster input
- [ ] Implement calculation history panel
- [ ] Add scientific calculator mode
- [ ] Create preset theme options
- [ ] Add unit conversion features
- [ ] Performance optimization for large numbers

---

## 📄 License

This project is licensed under the **MIT License** - see the LICENSE file for details.

```
MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software.
```

---

## 👨‍💻 Author

**guguluP**

- 🔗 [GitHub Profile](https://github.com/guguluP)
- 💬 Feel free to reach out with questions or suggestions

---

## 🙏 Acknowledgments

- Thanks to all contributors who've helped improve this project
- Inspiration from modern glassmorphism design trends
- The Java development community for excellent resources

---

## 📞 Support

If you encounter any issues or have questions:

1. **Check Existing Issues** - Browse [GitHub Issues](https://github.com/guguluP/Glass-Calculator-V2/issues)
2. **Create a New Issue** - If your problem isn't listed
3. **Discussion Forum** - Use GitHub Discussions for questions

---

## 🎓 Learning Resources

- [Java Swing Tutorial](https://docs.oracle.com/javase/tutorial/uiswing/)
- [CSS Glassmorphism Guide](https://css-tricks.com/)
- [Java Calculator Implementation](https://github.com/topics/java-calculator)

---

<div align="center">

### ⭐ If you find this project useful, please consider giving it a star!

Made with ❤️ by [guguluP](https://github.com/guguluP)

</div>
