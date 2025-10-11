// ===== API配置 =====
// API base URL is loaded from config.js
// API基础URL从config.js中加载
const API_BASE_URL = CONFIG.API_BASE_URL;

// 全局变量
let currentUser = null;
let currentStage = null;
let projects = [];
let stageConfig = {};

// ===== API调用函数 =====

/**
 * 获取当前比赛阶段
 */
async function fetchCurrentStage() {
    try {
        const response = await fetch(`${API_BASE_URL}/stage`);
        const result = await response.json();

        if (result.code === 200) {
            currentStage = result.data.code;
            stageConfig[currentStage] = {
                name: result.data.name,
                time: result.data.time,
                rule: result.data.rule,
                canInvest: result.data.canInvest
            };
            return result.data;
        } else {
            console.error('获取比赛阶段失败:', result.message);
            return null;
        }
    } catch (error) {
        console.error('获取比赛阶段异常:', error);
        return null;
    }
}

/**
 * 获取所有项目列表
 */
async function fetchProjects() {
    try {
        const response = await fetch(`${API_BASE_URL}/projects`);
        const result = await response.json();

        if (result.code === 200) {
            projects = result.data;
            return projects;
        } else {
            console.error('获取项目列表失败:', result.message);
            return [];
        }
    } catch (error) {
        console.error('获取项目列表异常:', error);
        return [];
    }
}

/**
 * 投资人登录
 */
async function login(username, password) {
    try {
        const response = await fetch(`${API_BASE_URL}/login`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ username, password })
        });

        const result = await response.json();

        if (result.code === 200) {
            return result.data;
        } else {
            return null;
        }
    } catch (error) {
        console.error('登录异常:', error);
        return null;
    }
}

/**
 * 获取投资人信息
 */
async function fetchInvestorInfo(username) {
    try {
        const response = await fetch(`${API_BASE_URL}/investor/${username}`);
        const result = await response.json();

        if (result.code === 200) {
            return result.data;
        } else {
            console.error('获取投资人信息失败:', result.message);
            return null;
        }
    } catch (error) {
        console.error('获取投资人信息异常:', error);
        return null;
    }
}

/**
 * 执行投资
 */
async function invest(investorUsername, projectId, amount) {
    try {
        const response = await fetch(`${API_BASE_URL}/invest`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                investorUsername,
                projectId,
                amount
            })
        });

        const result = await response.json();

        if (result.code === 200) {
            return { success: true, message: result.message };
        } else {
            return { success: false, message: result.message };
        }
    } catch (error) {
        console.error('投资异常:', error);
        return { success: false, message: '投资失败: ' + error.message };
    }
}

// ===== 页面初始化 =====

document.addEventListener('DOMContentLoaded', async function() {
    await initializeData();
    updateStageInfo();
    renderProjects();

    // 绑定事件
    document.getElementById('loginForm').addEventListener('submit', handleLogin);
    document.getElementById('investForm').addEventListener('submit', handleInvestment);
});

/**
 * 初始化数据（从后端加载）
 */
async function initializeData() {
    const stageData = await fetchCurrentStage();
    const projectsData = await fetchProjects();

    console.log('初始化完成:', { stage: currentStage, projectCount: projects.length });
}

// ===== UI更新函数 =====

/**
 * 更新阶段信息显示
 */
function updateStageInfo() {
    const stageInfo = document.getElementById('stageInfo');
    const config = stageConfig[currentStage];

    if (config) {
        stageInfo.innerHTML = `
            <div class="stage-current">
                <i class="fas fa-clock me-2"></i>当前阶段：${config.name}
            </div>
            <div class="stage-time mt-2">
                <i class="fas fa-calendar me-2"></i>时间：${config.time}
            </div>
            <div class="stage-rule mt-3">
                <i class="fas fa-rules me-2"></i>规则：${config.rule}
            </div>
        `;
    }
}

/**
 * 渲染项目列表
 */
function renderProjects() {
    const qualifiedProjects = projects.filter(p => p.qualified);
    const nonQualifiedProjects = projects.filter(p => !p.qualified);

    // 显示/隐藏晋级区
    const qualifiedSection = document.getElementById('qualifiedSection');
    const nonQualifiedTitle = document.getElementById('nonQualifiedTitle');

    if (currentStage === 'lock' || currentStage === 'investment' || currentStage === 'ended') {
        qualifiedSection.style.display = 'block';
        nonQualifiedTitle.innerHTML = '<i class="fas fa-list me-2"></i>非晋级区';
        renderProjectList('qualifiedProjects', qualifiedProjects, true);
        renderProjectList('nonQualifiedProjects', nonQualifiedProjects, false);
    } else {
        qualifiedSection.style.display = 'none';
        nonQualifiedTitle.innerHTML = '<i class="fas fa-list me-2"></i>参赛作品';
        renderProjectList('nonQualifiedProjects', projects, false);
    }
}

/**
 * 渲染项目列表
 */
function renderProjectList(containerId, projectList, isQualified) {
    const container = document.getElementById(containerId);

    container.innerHTML = projectList.map((project, index) => {
        const rankClass = project.rank <= 3 ? 'top-3' : '';
        const canInvest = isQualified && stageConfig[currentStage]?.canInvest;

        return `
            <div class="project-card position-relative fade-in" style="animation-delay: ${index * 0.1}s">
                <div class="project-rank ${rankClass}">
                    #${project.rank}
                </div>

                <div class="row align-items-center">
                    <div class="col-auto">
                        <img src="${project.image || './default-project.svg'}" alt="${project.name}" class="project-image">
                    </div>

                    <div class="col">
                        <div class="project-title">${project.name}</div>
                        <div class="project-description">${project.description}</div>
                        <a href="${project.url}" target="_blank" class="project-url">
                            <i class="fas fa-external-link-alt me-1"></i>${project.url}
                        </a>

                        <div class="mt-2">
                            <div class="team-info" onclick="window.open('${project.teamUrl}', '_blank')">
                                <span class="team-name">${project.teamName}</span>
                                <span class="team-number">#${project.teamNumber}</span>
                            </div>
                        </div>

                        <div class="stats-container">
                            <div class="stat-item">
                                <i class="fas fa-eye stat-icon"></i>
                                <span class="stat-label">累计UV:</span>
                                <span class="stat-value">${project.uv.toLocaleString()}</span>
                            </div>
                            <div class="stat-item">
                                <i class="fas fa-coins stat-icon"></i>
                                <span class="stat-label">获得投资:</span>
                                <span class="stat-value">${project.investment}万元</span>
                            </div>
                            ${project.investmentRecords && project.investmentRecords.length > 0 ? `
                                <div class="stat-item">
                                    <i class="fas fa-users stat-icon"></i>
                                    <span class="stat-label">投资记录:</span>
                                    <div class="investment-records">
                                        ${project.investmentRecords.map(record => `
                                            <div class="investor-avatar"
                                                 style="background-image: url('${record.avatar || './default-avatar.svg'}')"
                                                 title="${record.name} - ${record.amount}万元">
                                                <div class="investment-amount">${record.amount}</div>
                                            </div>
                                        `).join('')}
                                    </div>
                                </div>
                            ` : ''}
                        </div>

                        <div class="action-buttons">
                            <button class="btn btn-visit" onclick="window.open('${project.url}', '_blank')">
                                <i class="fas fa-external-link-alt me-2"></i>访问
                            </button>
                            ${isQualified ? `
                                <button class="btn btn-invest ${!canInvest ? 'disabled' : ''}"
                                        onclick="showInvestModal(${project.id})"
                                        ${!canInvest ? 'disabled' : ''}>
                                    <i class="fas fa-coins me-2"></i>投资
                                </button>
                            ` : ''}
                        </div>
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

// ===== 事件处理函数 =====

/**
 * 显示登录模态框
 */
function showLoginModal() {
    const modal = new bootstrap.Modal(document.getElementById('loginModal'));
    modal.show();
}

/**
 * 处理登录
 */
async function handleLogin(e) {
    e.preventDefault();

    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;

    const investor = await login(username, password);

    if (investor) {
        currentUser = investor;
        showToast('登录成功！', 'success');
        bootstrap.Modal.getInstance(document.getElementById('loginModal')).hide();

        // 更新登录按钮
        const loginBtn = document.querySelector('[onclick="showLoginModal()"]');
        loginBtn.innerHTML = `
            <img src="${investor.avatar || './default-avatar.svg'}" alt="${investor.name}"
                 style="width: 24px; height: 24px; border-radius: 50%; margin-right: 8px;">
            ${investor.name}
        `;
        loginBtn.onclick = () => showInvestorPage();

        // 重置表单
        document.getElementById('loginForm').reset();
    } else {
        showToast('账号或密码错误！', 'error');
    }
}

/**
 * 显示投资模态框
 */
function showInvestModal(projectId) {
    if (!stageConfig[currentStage]?.canInvest) {
        showToast('当前阶段不可投资，请见大赛规则', 'warning');
        return;
    }

    if (!currentUser) {
        showToast('请先登录投资人账号', 'warning');
        showLoginModal();
        return;
    }

    const project = projects.find(p => p.id === projectId);
    if (!project) return;

    if (!project.qualified) {
        showToast('只能投资晋级的前15名作品', 'warning');
        return;
    }

    const investInfo = document.getElementById('investInfo');
    investInfo.innerHTML = `
        <div class="glass-card">
            <h6 class="text-white">${project.name}</h6>
            <p class="text-light">${project.teamName}#${project.teamNumber}</p>
            <p class="text-info small">${project.description}</p>
        </div>
    `;

    document.getElementById('remainingAmount').textContent = currentUser.remainingAmount;
    document.getElementById('investAmount').max = currentUser.remainingAmount;
    document.getElementById('investForm').dataset.projectId = projectId;

    const modal = new bootstrap.Modal(document.getElementById('investModal'));
    modal.show();
}

/**
 * 处理投资
 */
async function handleInvestment(e) {
    e.preventDefault();

    const projectId = parseInt(document.getElementById('investForm').dataset.projectId);
    const amount = parseInt(document.getElementById('investAmount').value);

    if (amount > currentUser.remainingAmount) {
        showToast('投资金额超过剩余额度！', 'error');
        return;
    }

    const result = await invest(currentUser.username, projectId, amount);

    if (result.success) {
        showToast(`成功投资${amount}万元！`, 'success');
        bootstrap.Modal.getInstance(document.getElementById('investModal')).hide();

        // 更新当前用户剩余额度
        currentUser.remainingAmount -= amount;

        // 重新加载项目列表
        await fetchProjects();
        renderProjects();

        // 重置表单
        document.getElementById('investForm').reset();
    } else {
        showToast(result.message || '投资失败', 'error');
    }
}

/**
 * 显示投资人页面
 */
async function showInvestorPage() {
    if (!currentUser) return;

    // 刷新投资人信息
    const investor = await fetchInvestorInfo(currentUser.username);
    if (investor) {
        currentUser = investor;
    }

    // ... 其余代码同原script.js的showInvestorPage函数 ...
    // （为节省篇幅，这里省略，可以复用原来的代码）
}

/**
 * 返回主页面
 */
function backToMainPage() {
    document.querySelector('.main-content').style.display = 'block';
    const investorPage = document.querySelector('.investor-page');
    if (investorPage) {
        investorPage.remove();
    }
}

/**
 * 显示Toast提示
 */
function showToast(message, type = 'info') {
    const toast = document.getElementById('toast');
    const toastBody = toast.querySelector('.toast-body');
    const toastHeader = toast.querySelector('.toast-header i');

    toastBody.textContent = message;

    toastHeader.className = `fas me-2 ${
        type === 'success' ? 'fa-check-circle text-success' :
        type === 'error' ? 'fa-exclamation-circle text-danger' :
        type === 'warning' ? 'fa-exclamation-triangle text-warning' :
        'fa-info-circle text-primary'
    }`;

    const bsToast = new bootstrap.Toast(toast);
    bsToast.show();
}
