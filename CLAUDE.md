# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the official website for the GDTech 8th Hackathon (GDTech第八届骇客大赛), a competition showcasing student projects in educational technology. The site features:
- Competition entries display and ranking system
- Investor login and virtual investment functionality
- Multi-stage competition management (Selection → Lock → Investment → Ended)
- Modern glassmorphism UI design

**Theme**: AI X Edu, Ignite the Future of Education
**Organizer**: 高顿集团科技研发中心
**Sponsor**: 字节跳动火山引擎

## Project Architecture

This project now consists of **two parts**:

1. **Frontend** (静态HTML+JS) - Located in root directory
2. **Backend** (Spring Boot + 飞书多维表格) - Located in `/backend` directory

### Architecture Diagram

```
Frontend (Browser)
    ↓ HTTP/REST API
Backend (Spring Boot :8080)
    ↓ HTTP API (Data Storage)
Feishu Bitable (飞书多维表格)

Backend (定时任务)
    ↓ HTTP API (UV Data)
Baidu Tongji (百度统计)
```

## Development Commands

### Backend Development

```bash
# Navigate to backend directory
cd backend

# Compile and run
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Or run packaged jar
mvn clean package
java -jar target/hackathon-1.0.0.jar --spring.profiles.active=dev
```

Backend runs on: `http://localhost:8080/api`

**See `backend/README.md` for detailed backend documentation.**

### Frontend Development

```bash
# Start local server with Python (from root directory)
python -m http.server 8000

# Or with Node.js
npx http-server

# Or with PHP
php -S localhost:8000
```

Frontend runs on: `http://localhost:8000`

**Important**: Update `script-api.js` line 2 to point to your backend URL if different.

## Architecture and Core Concepts

### File Structure

**Frontend (Root Directory):**
- `index.html` - Main page structure and layout
- `script.js` - **LEGACY** Static demo with hardcoded data
- `script-api.js` - **NEW** API version calling backend
- `styles-new.css` - Glassmorphism UI styles and responsive design
- `styles.css` - Legacy styles (may be unused)
- `default-project.svg` - Default project avatar
- `default-avatar.svg` - Default investor avatar

**Backend (backend/ Directory):**
- `src/main/java/com/gdtech/hackathon/`
  - `HackathonApplication.java` - Main entry point
  - `config/` - Configuration classes (Feishu, Cache, CORS)
  - `controller/HackathonController.java` - REST API endpoints
  - `service/FeishuService.java` - Feishu API integration
  - `service/HackathonService.java` - Core business logic
  - `model/` - Data models (Project, Investor, etc.)
  - `dto/` - Request/Response DTOs
- `pom.xml` - Maven dependencies
- `application.yml` - Configuration file
- `README.md` - Backend documentation

### Competition Stage System

The application operates in four distinct stages, controlled by the `currentStage` variable in `script.js`:

1. **Selection Period** (`'selection'`): 2025/10/25 0:00 - 2025/11/7 12:00
   - Ranking by cumulative UV (unique visitors) from 2025/10/25 0:00 onwards
   - Top 15 advance to investment period
   - No investment allowed

2. **Lock Period** (`'lock'`): 2025/11/7 12:00 - 2025/11/14 0:00
   - Qualified projects (top 15) displayed separately
   - Non-qualified projects shown in separate section
   - Both ranked by UV
   - No investment allowed

3. **Investment Period** (`'investment'`): 11/14 0:00 - 18:00
   - Investors can invest in top 15 qualified projects
   - Ranking formula: `weight = UV * 0.4 + investment * 0.6`
   - Ties broken by: investment amount → team number
   - Investment is enabled

4. **Ended** (`'ended'`): After 11/14 18:00
   - All data frozen
   - No updates or investments

### Ranking Logic

The `sortProjects()` function in script.js:217 implements stage-specific ranking:
- **Investment/Ended stages**: Weighted score (UV 40% + investment 60%)
- **Selection/Lock stages**: Pure UV ranking
- **Tiebreakers**: Always use team number (ascending)

### Data Structure

**Projects** (script.js:38):
```javascript
{
  id, name, description, url, image,
  teamName, teamNumber, teamUrl,
  uv,                    // Cumulative unique visitors
  investment,            // Total investment received (万元)
  investmentRecords: []  // Array of investor records
}
```

**Investors** (script.js:121):
```javascript
{
  username,              // 4-digit number (e.g., "1001")
  password,              // 6-char alphanumeric (e.g., "123abc")
  name, title, avatar,
  initialAmount,         // Starting capital (万元)
  remainingAmount,       // Available capital
  investmentHistory: []  // Investment records
}
```

### Key Functions

- `initializeData()` (script.js:36) - Initializes all project and investor data
- `setCurrentStage()` (script.js:159) - Sets competition stage (currently hardcoded to 'investment')
- `renderProjects()` (script.js:195) - Renders project list with qualified/non-qualified separation
- `handleLogin()` (script.js:323) - Investor authentication
- `handleInvestment()` (script.js:519) - Processes investment transactions
- `simulateDataUpdate()` (script.js:609) - Randomly updates UV data every 30 seconds

### Important Constraints

1. **Only qualified projects** (top 15) can receive investments
2. **Investment period only**: Investments blocked in other stages
3. **No backend**: All data is client-side and resets on refresh
4. **Test investor accounts** are hardcoded in script.js:121-152

## Customization

### Change Competition Stage
Edit `script.js:162`:
```javascript
currentStage = 'investment'; // 'selection', 'lock', 'investment', 'ended'
```

### Add Projects
Add to `projects` array in `initializeData()` (script.js:38):
```javascript
projects.push({
  id: 21,
  name: '项目名称',
  description: '项目描述',
  url: 'https://example.com',
  image: './default-project.svg',
  teamName: '团队名称',
  teamNumber: '021',
  teamUrl: 'https://team-url.com',
  uv: 5000,
  investment: 0,
  investmentRecords: []
});
```

### Add Investors
Add to `investors` array in `initializeData()` (script.js:121):
```javascript
investors.push({
  username: '1004',
  password: 'abc123',
  name: '投资人姓名',
  title: '职位',
  avatar: './default-avatar.svg',
  initialAmount: 100,
  remainingAmount: 100,
  investmentHistory: []
});
```

## Technology Stack

- **Frontend**: Vanilla JavaScript (ES6+), HTML5, CSS3
- **UI Framework**: Bootstrap 5.3.0
- **Icons**: Font Awesome 6.0.0
- **Design**: Glassmorphism with gradient backgrounds
- **No build tools required** - runs directly in browser

## Browser Support

- Chrome 60+
- Firefox 55+
- Safari 12+
- Edge 79+

## Requirements & Implementation Gap Analysis

### Current Implementation vs Requirements

**⚠️ CRITICAL: Ranking Algorithm Discrepancy**
- **Requirements**: Investment period ranking should be `UV排名*40% + 投资额排名*60%` (rank-based weighted)
- **Current Code**: Uses `UV*0.4 + investment*0.6` (value-based weighted) in script.js:221
- **Action needed**: Clarify with stakeholders which formula is correct

**Background Image Requirements**
- **Requirements**: Support animated GIF, clickable to WeChat article
- **Current Code**: Static SVG background (index.html:13)
- **Action needed**: Replace with animated background or implement animation

**Baidu Analytics Integration**
- **Requirements**:
  - Integrate Baidu Analytics
  - Each project has own analytics account + site ID
  - Fetch UV data periodically (every XX minutes)
- **Current Code**: Mock UV data with random simulation (script.js:609)
- **Action needed**: Implement Baidu Analytics API integration

**Data Management System**
- **Requirements**: Need admin interface for:
  - Project information entry (考虑飞书表格/markdown/其他方案)
  - Investor account management (账号+密码+额度)
- **Current Code**: Hardcoded data in JavaScript arrays
- **Action needed**: Build data entry/import system

### Correctly Implemented Features

✅ Four-stage competition system with time-based transitions
✅ Investor login with 4-digit username + 6-char alphanumeric password validation
✅ Investment restrictions (only qualified top 15, only during investment period)
✅ Investment is irreversible (整数，万元，不可撤回，不可修改)
✅ Toast notification for investment outside allowed period
✅ Investor page showing avatar, name, title, initial/remaining amounts, history
✅ Investment records with timestamp, project name, team name & number, amount
✅ Separate display of qualified vs non-qualified zones during lock/investment/ended periods
✅ Project info: image, name, description, URL, team name & number (clickable to team intro)
✅ Investment details showing investor avatars with hover card (name, title, amount)
✅ "Visit" button to access project URL
✅ Dark theme with tech-style UI (glassmorphism)

### Known Edge Case (Confirmed OK)
- Lock period: Non-qualified projects may have higher UV than qualified ones
- **Status**: Confirmed with organizing committee - no action needed

## Future Integration Notes

This is currently a **static demo** with client-side data. For production deployment:

1. **Backend API**: Replace `initializeData()` with API calls
2. **Baidu Analytics Integration**:
   - Set up analytics accounts for each project
   - Implement scheduled UV data fetching (考虑后端定时任务)
   - Replace mock data with real analytics data
3. **Data Management**:
   - Build admin interface for project/investor entry
   - Consider: Feishu spreadsheet integration / Markdown import / Custom admin panel
4. **Authentication**: Replace hardcoded credentials with secure auth system
5. **Persistence**: Store data in database instead of JavaScript arrays
6. **Stage transitions**: Automate stage changes based on server time
7. **Background**: Implement animated background (支持动图)
