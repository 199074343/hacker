// ===== API配置 =====
// API base URL is loaded from config.js
// API基础URL从config.js中加载
const API_BASE_URL = CONFIG.API_BASE_URL;

// 全局变量
let currentUser = null;
let currentStage = null;
let projects = [];
let stageConfig = {};
let isSubmittingInvestment = false;

// ===== Loading辅助函数 =====

/**
 * 显示全局loading效果
 */
function showLoading() {
    const loadingEl = document.getElementById('globalLoading');
    if (loadingEl) {
        loadingEl.style.display = 'flex';
    }
}

/**
 * 隐藏全局loading效果
 */
function hideLoading() {
    const loadingEl = document.getElementById('globalLoading');
    if (loadingEl) {
        loadingEl.style.display = 'none';
    }
}

/**
 * 切换投资按钮状态，避免重复提交
 */
function setInvestButtonState(isLoading) {
    const submitBtn = document.getElementById('investSubmitBtn');
    if (!submitBtn) return;

    if (isLoading) {
        if (!submitBtn.dataset.originalHtml) {
            submitBtn.dataset.originalHtml = submitBtn.innerHTML;
        }
        submitBtn.disabled = true;
        submitBtn.innerHTML = `
            <span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
            确认中...
        `;
    } else {
        submitBtn.disabled = false;
        submitBtn.innerHTML = submitBtn.dataset.originalHtml || '确认投资';
        delete submitBtn.dataset.originalHtml;
    }
}

// ===== API调用函数 =====

/**
 * 获取当前比赛阶段
 */
async function fetchCurrentStage() {
    try {
        showLoading();
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
    } finally {
        hideLoading();
    }
}

/**
 * 获取所有项目列表
 */
async function fetchProjects() {
    try {
        showLoading();
        const response = await fetch(`${API_BASE_URL}/projects`);
        const result = await response.json();

        if (result.code === 200) {
            projects = result.data;

            // 如果是投资期或结束期，打印排名详情
            if (currentStage === 'investment' || currentStage === 'ended') {
                printRankingDetails(projects);
            }

            return projects;
        } else {
            console.error('获取项目列表失败:', result.message);
            return [];
        }
    } catch (error) {
        console.error('获取项目列表异常:', error);
        return [];
    } finally {
        hideLoading();
    }
}

/**
 * 打印投资期排名详情到浏览器控制台
 */
function printRankingDetails(projects) {
    console.log('%c================== 投资期排名计算详情 ==================', 'color: #00f2fe; font-size: 16px; font-weight: bold');
    console.log(`总队伍数: ${projects.length}`);
    console.log('');

    // 按UV排序
    const uvSorted = [...projects].sort((a, b) => {
        if (b.uv !== a.uv) return b.uv - a.uv;
        return (a.teamNumber || '999').localeCompare(b.teamNumber || '999');
    });

    // 按投资额排序
    const investmentSorted = [...projects].sort((a, b) => {
        if (b.investment !== a.investment) return b.investment - a.investment;
        return (a.teamNumber || '999').localeCompare(b.teamNumber || '999');
    });

    // 计算排名
    const uvRankMap = {};
    const investRankMap = {};
    uvSorted.forEach((p, i) => { uvRankMap[p.id] = i + 1; });
    investmentSorted.forEach((p, i) => { investRankMap[p.id] = i + 1; });

    // 打印每个项目的详细信息
    projects.forEach(p => {
        const totalTeams = projects.length;
        const uvRank = uvRankMap[p.id];
        const investRank = investRankMap[p.id];
        const uvScore = (totalTeams + 1 - uvRank) / totalTeams * 100;
        const investScore = (totalTeams + 1 - investRank) / totalTeams * 100;
        const weightedScore = uvScore * 0.4 + investScore * 0.6;

        console.log(`%c项目ID: ${p.id}, 名称: ${p.name}, 队伍编号: ${p.teamNumber}`, 'color: #4facfe; font-weight: bold');
        console.log(`  UV: ${p.uv}, UV排名: ${uvRank}, UV排名分数: ${uvScore.toFixed(2)}`);
        console.log(`  投资额: ${p.investment}万元, 投资额排名: ${investRank}, 投资额排名分数: ${investScore.toFixed(2)}`);
        console.log(`  最终加权分数: ${weightedScore.toFixed(2)} (${uvScore.toFixed(2)}*0.4 + ${investScore.toFixed(2)}*0.6)`);
        console.log(`  后端返回的加权分数: ${p.weightedScore?.toFixed(2) || 'N/A'}`);
        console.log('---');
    });

    console.log('');
    console.log('%c================== 最终排名结果 ==================', 'color: #00f2fe; font-size: 16px; font-weight: bold');
    projects.slice(0, 20).forEach((p, i) => {
        console.log(`%c排名#${p.rank}: ${p.name} (队伍#${p.teamNumber})`, 'color: #ffd700; font-weight: bold');
        console.log(`  加权分数: ${p.weightedScore?.toFixed(2) || 'N/A'}, UV: ${p.uv}, 投资: ${p.investment}万元`);
    });
    console.log('');
    console.log('%c排名算法: UV排名40%权重 + 投资额排名60%权重', 'color: #4facfe; font-style: italic');
}

/**
 * 投资人登录
 */
async function login(username, password) {
    try {
        showLoading();
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
    } finally {
        hideLoading();
    }
}

/**
 * 获取投资人信息
 */
async function fetchInvestorInfo(username) {
    try {
        showLoading();
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
    } finally {
        hideLoading();
    }
}

/**
 * 执行投资
 */
async function invest(investorUsername, projectId, amount) {
    try {
        showLoading();
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
    } finally {
        hideLoading();
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

    console.log('初始化完成:', {
        stage: currentStage,
        projectCount: projects.length,
        canInvest: stageConfig[currentStage]?.canInvest
    });
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

    const sortedList = isQualified
        ? [...projectList]
        : [...projectList].sort((a, b) => {
            if (b.uv !== a.uv) return b.uv - a.uv;
            return (a.teamNumber || '999').localeCompare(b.teamNumber || '999');
        });

    container.innerHTML = sortedList.map((project, index) => {
        const rankClass = project.rank <= 3 ? 'top-3' : '';
        const canInvest = isQualified && stageConfig[currentStage]?.canInvest;
        const showInvestButton = isQualified && canInvest;

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
                            ${showInvestButton ? `
                                <button class="btn btn-invest"
                                        onclick="showInvestModal(${project.id})">
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

        // 自动跳转到投资人页面
        setTimeout(() => {
            showInvestorPage();
        }, 500); // 延迟500ms，等待toast显示和模态框关闭动画
    } else {
        showToast('账号或密码错误！', 'error');
    }
}

/**
 * 显示投资模态框
 */
function showInvestModal(projectId) {
    // 调试日志
    console.log('投资按钮点击 - 当前阶段:', currentStage, '是否可投资:', stageConfig[currentStage]?.canInvest);

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
    setInvestButtonState(false);

    const modal = new bootstrap.Modal(document.getElementById('investModal'));
    modal.show();
}

/**
 * 处理投资
 */
async function handleInvestment(e) {
    e.preventDefault();

    if (isSubmittingInvestment) {
        return;
    }

    const investForm = document.getElementById('investForm');
    const projectId = parseInt(investForm.dataset.projectId, 10);
    const amount = parseInt(document.getElementById('investAmount').value, 10);

    if (!projectId) {
        showToast('未找到项目，请重试', 'error');
        return;
    }

    if (!Number.isFinite(amount) || amount <= 0) {
        showToast('请输入有效的投资金额', 'error');
        return;
    }

    if (amount > currentUser.remainingAmount) {
        showToast('投资金额超过剩余额度！', 'error');
        return;
    }

    isSubmittingInvestment = true;
    setInvestButtonState(true);

    try {
        const result = await invest(currentUser.username, projectId, amount);

        if (result.success) {
            showToast(`成功投资${amount}万元！`, 'success');

            const modalElement = document.getElementById('investModal');
            const modalInstance = bootstrap.Modal.getInstance(modalElement);
            if (modalInstance) {
                modalInstance.hide();
            }

            // 同步刷新投资人信息和项目数据
            currentUser.remainingAmount -= amount;
            currentUser.investedAmount = (currentUser.investedAmount || 0) + amount;

            const investor = await fetchInvestorInfo(currentUser.username);
            if (investor) {
                currentUser = investor;
            }

            await fetchProjects();
            renderProjects();

            investForm.reset();
        } else {
            showToast(result.message || '投资失败', 'error');
        }
    } catch (error) {
        console.error('投资提交异常:', error);
        showToast('投资失败，请稍后再试', 'error');
    } finally {
        isSubmittingInvestment = false;
        setInvestButtonState(false);
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

    // 隐藏主要内容，显示投资人页面
    document.querySelector('.main-content').style.display = 'none';

    // 创建投资人页面内容
    const investorPageHTML = `
        <div class="container-fluid investor-page" style="padding-top: 100px; padding-bottom: 50px;">
            <div class="row justify-content-center">
                <div class="col-lg-10">
                    <!-- 返回按钮 -->
                    <div class="mb-4">
                        <button class="btn glass-btn" onclick="backToMainPage()">
                            <i class="fas fa-arrow-left me-2"></i>返回主页
                        </button>
                    </div>

                    <!-- 投资人信息卡片 -->
                    <div class="glass-card mb-4">
                        <div class="row align-items-center">
                            <div class="col-auto">
                                <img src="${currentUser.avatar || './default-avatar.svg'}" alt="${currentUser.name}"
                                     style="width: 80px; height: 80px; border-radius: 50%; border: 3px solid rgba(255,255,255,0.3);">
                            </div>
                            <div class="col">
                                <h3 class="text-white mb-1">${currentUser.name}</h3>
                                <p class="text-light mb-1">${currentUser.title}</p>
                                <p class="text-info mb-0">账号：${currentUser.username}</p>
                            </div>
                        </div>
                    </div>

                    <!-- 投资额度信息 -->
                    <div class="row mb-4">
                        <div class="col-md-4">
                            <div class="glass-card text-center">
                                <h6 class="text-warning mb-2">
                                    <i class="fas fa-wallet me-2"></i>初始额度
                                </h6>
                                <h3 class="text-white">${currentUser.initialAmount}万元</h3>
                            </div>
                        </div>
                        <div class="col-md-4">
                            <div class="glass-card text-center">
                                <h6 class="text-info mb-2">
                                    <i class="fas fa-coins me-2"></i>剩余额度
                                </h6>
                                <h3 class="text-white">${currentUser.remainingAmount}万元</h3>
                            </div>
                        </div>
                        <div class="col-md-4">
                            <div class="glass-card text-center">
                                <h6 class="text-success mb-2">
                                    <i class="fas fa-chart-line me-2"></i>已投资
                                </h6>
                                <h3 class="text-white">${currentUser.investedAmount || (currentUser.initialAmount - currentUser.remainingAmount)}万元</h3>
                            </div>
                        </div>
                    </div>

                    <!-- 投资记录 -->
                    <div class="glass-card">
                        <h4 class="text-white mb-3">
                            <i class="fas fa-history me-2"></i>投资记录
                        </h4>
                        ${currentUser.investmentHistory && currentUser.investmentHistory.length > 0 ? `
                            <div class="investment-history">
                                ${currentUser.investmentHistory.map((record, index) => `
                                    <div class="investment-record-item glass-card mb-3 fade-in" style="animation-delay: ${index * 0.1}s">
                                        <div class="row align-items-center">
                                            <div class="col-md-6">
                                                <h6 class="text-white mb-1">${record.projectName}</h6>
                                                <p class="text-info small mb-0">
                                                    <i class="fas fa-clock me-1"></i>${formatTime(record.time)}
                                                </p>
                                            </div>
                                            <div class="col-md-6 text-md-end">
                                                <div class="investment-amount-display">
                                                    <span class="text-warning fw-bold fs-4">${record.amount}万元</span>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                `).join('')}
                            </div>
                        ` : `
                            <div class="text-center py-5">
                                <i class="fas fa-inbox text-muted" style="font-size: 3rem; opacity: 0.3;"></i>
                                <p class="text-light mt-3">暂无投资记录</p>
                            </div>
                        `}
                    </div>
                </div>
            </div>
        </div>
    `;

    // 在主要内容后插入投资人页面
    document.querySelector('.main-content').insertAdjacentHTML('afterend', investorPageHTML);
}

/**
 * 格式化时间显示
 */
function formatTime(time) {
    if (!time) return '';

    // 如果是LocalDateTime对象(从API返回)，格式化显示
    if (typeof time === 'object' && time.year) {
        return `${time.year}-${String(time.monthValue).padStart(2, '0')}-${String(time.dayOfMonth).padStart(2, '0')} ${String(time.hour).padStart(2, '0')}:${String(time.minute).padStart(2, '0')}:${String(time.second).padStart(2, '0')}`;
    }

    // 如果是字符串，直接返回
    return time.toString();
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
