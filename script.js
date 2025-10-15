// 全局变量
let currentUser = null;
let currentStage = 'selection'; // selection, lock, investment, ended
let projects = [];
let investors = [];

// 比赛阶段配置
const stageConfig = {
    selection: {
        name: '海选期',
        time: '10月24日24:00 - 11月7日12:00',
        rule: '本阶段以累计UV排名，如果UV相同，则按队伍序号排名。本阶段结束，前15名晋级，在投资期可以接受投资人投资',
        canInvest: false
    },
    lock: {
        name: '锁定期',
        time: '11月7日12:00 - 11月14日0:00',
        rule: '本阶段期间，已晋级的15个作品一个队列，按UV排名；其他作品处在非晋级区，单独一个队列，依然按照UV排名',
        canInvest: false
    },
    investment: {
        name: '投资期',
        time: '11月14日0:00 - 18:00',
        rule: '本阶段，投资人可将虚拟投资金投给晋级的15个作品。本阶段排名按照权重值（UV*40%+投资金额*60%）排序，权重相同按投资金额高低排序，投资金额相同按队伍序号排序',
        canInvest: true
    },
    ended: {
        name: '活动结束',
        time: '11月14日18:00之后',
        rule: '活动结束，所有作品不再更新UV、投资额数据，排名不变',
        canInvest: false
    }
};

// 初始化数据
function initializeData() {
    // 模拟参赛作品数据
    projects = [
        {
            id: 1,
            name: 'AI智能学习助手',
            description: '基于大语言模型的个性化学习辅导平台',
            url: 'https://ai-tutor.example.com',
            image: './default-project.svg',
            teamName: '智慧教育团队',
            teamNumber: '001',
            teamUrl: 'https://www.baidu.com',
            uv: 15420,
            investment: 0,
            investmentRecords: []
        },
        {
            id: 2,
            name: '虚拟实验室',
            description: '沉浸式VR教学实验环境',
            url: 'https://vr-lab.example.com',
            image: './default-project.svg',
            teamName: '未来科技',
            teamNumber: '002',
            teamUrl: 'https://www.baidu.com',
            uv: 14850,
            investment: 0,
            investmentRecords: []
        },
        {
            id: 3,
            name: '智能作业批改系统',
            description: 'AI驱动的自动作业评分与反馈系统',
            url: 'https://homework-ai.example.com',
            image: './default-project.svg',
            teamName: '教育创新者',
            teamNumber: '003',
            teamUrl: 'https://www.baidu.com',
            uv: 13920,
            investment: 0,
            investmentRecords: []
        },
        {
            id: 4,
            name: '个性化学习路径',
            description: '基于学习数据分析的个性化课程推荐',
            url: 'https://learning-path.example.com',
            image: './default-project.svg',
            teamName: '数据驱动',
            teamNumber: '004',
            teamUrl: 'https://www.baidu.com',
            uv: 12680,
            investment: 0,
            investmentRecords: []
        },
        {
            id: 5,
            name: '在线协作白板',
            description: '支持多人实时协作的智能教学白板',
            url: 'https://collab-board.example.com',
            image: './default-project.svg',
            teamName: '协作先锋',
            teamNumber: '005',
            teamUrl: 'https://www.baidu.com',
            uv: 11450,
            investment: 0,
            investmentRecords: []
        },
        // 添加更多项目以达到20个
        ...Array.from({length: 15}, (_, i) => ({
            id: i + 6,
            name: `创新教育项目${i + 6}`,
            description: `第${i + 6}个创新教育解决方案`,
            url: `https://project${i + 6}.example.com`,
            image: './default-project.svg',
            teamName: `团队${i + 6}`,
            teamNumber: String(i + 6).padStart(3, '0'),
            teamUrl: 'https://www.baidu.com',
            uv: Math.floor(Math.random() * 10000) + 1000,
            investment: 0,
            investmentRecords: []
        }))
    ];

    // 模拟投资人数据
    investors = [
        {
            username: '1001',
            password: '123abc',
            name: '张投资',
            title: '高级投资经理',
            avatar: './default-avatar.svg',
            initialAmount: 100,
            remainingAmount: 100,
            investmentHistory: []
        },
        {
            username: '1002',
            password: '456def',
            name: '李资本',
            title: '投资总监',
            avatar: './default-avatar.svg',
            initialAmount: 150,
            remainingAmount: 150,
            investmentHistory: []
        },
        {
            username: '1003',
            password: '789ghi',
            name: '王创投',
            title: '合伙人',
            avatar: './default-avatar.svg',
            initialAmount: 200,
            remainingAmount: 200,
            investmentHistory: []
        }
    ];

    // 根据当前时间设置比赛阶段（这里为了演示，可以手动设置）
    setCurrentStage();
}

// 设置当前比赛阶段
function setCurrentStage() {
    const now = new Date();
    // 这里可以根据实际时间来判断阶段，为了演示方便，我们设置为投资期
    currentStage = 'investment'; // 可以改为 'selection', 'lock', 'investment', 'ended'
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    initializeData();
    updateStageInfo();
    renderProjects();
    
    // 绑定事件
    document.getElementById('loginForm').addEventListener('submit', handleLogin);
    document.getElementById('investForm').addEventListener('submit', handleInvestment);
});

// 更新阶段信息显示
function updateStageInfo() {
    const stageInfo = document.getElementById('stageInfo');
    const config = stageConfig[currentStage];
    
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

// 渲染项目列表
function renderProjects() {
    const sortedProjects = sortProjects();
    const qualifiedProjects = sortedProjects.slice(0, 15);
    let nonQualifiedProjects = sortedProjects.slice(15);
    
    if (currentStage === 'investment' || currentStage === 'ended') {
        nonQualifiedProjects = nonQualifiedProjects.sort((a, b) => {
            if (b.uv !== a.uv) return b.uv - a.uv;
            return parseInt(a.teamNumber) - parseInt(b.teamNumber);
        });
    }
    
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
        renderProjectList('nonQualifiedProjects', sortedProjects, false);
    }
}

// 排序项目
function sortProjects() {
    return [...projects].sort((a, b) => {
        if (currentStage === 'investment' || currentStage === 'ended') {
            // 投资期：权重值排序
            const weightA = a.uv * 0.4 + a.investment * 0.6;
            const weightB = b.uv * 0.4 + b.investment * 0.6;
            
            if (weightA !== weightB) return weightB - weightA;
            if (a.investment !== b.investment) return b.investment - a.investment;
            return parseInt(a.teamNumber) - parseInt(b.teamNumber);
        } else {
            // 其他阶段：UV排序
            if (a.uv !== b.uv) return b.uv - a.uv;
            return parseInt(a.teamNumber) - parseInt(b.teamNumber);
        }
    });
}

// 渲染项目列表
function renderProjectList(containerId, projectList, isQualified) {
    const container = document.getElementById(containerId);
    const sortedList = isQualified
        ? [...projectList]
        : [...projectList].sort((a, b) => {
            if (b.uv !== a.uv) return b.uv - a.uv;
            return parseInt(a.teamNumber) - parseInt(b.teamNumber);
        });
    
    container.innerHTML = sortedList.map((project, index) => {
        const rank = isQualified ? index + 1 : index + 16;
        const rankClass = rank <= 3 ? 'top-3' : '';
        const canInvest = isQualified && stageConfig[currentStage].canInvest;
        
        return `
            <div class="project-card position-relative fade-in" style="animation-delay: ${index * 0.1}s">
                <div class="project-rank ${rankClass}">
                    #${rank}
                </div>
                
                <div class="row align-items-center">
                    <div class="col-auto">
                        <img src="${project.image}" alt="${project.name}" class="project-image">
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
                            ${project.investmentRecords.length > 0 ? `
                                <div class="stat-item">
                                    <i class="fas fa-users stat-icon"></i>
                                    <span class="stat-label">投资记录:</span>
                                    <div class="investment-records">
                                        ${project.investmentRecords.map(record => `
                                            <div class="investor-avatar" 
                                                 style="background-image: url('${record.avatar}')"
                                                 onmouseenter="showInvestorTooltip(event, '${record.name}', '${record.title}', ${record.amount})"
                                                 onmouseleave="hideInvestorTooltip()">
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
                            ${isQualified && canInvest ? `
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

// 显示登录模态框
function showLoginModal() {
    const modal = new bootstrap.Modal(document.getElementById('loginModal'));
    modal.show();
}

// 处理登录
function handleLogin(e) {
    e.preventDefault();
    
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;
    
    const investor = investors.find(inv => inv.username === username && inv.password === password);
    
    if (investor) {
        currentUser = investor;
        showToast('登录成功！', 'success');
        bootstrap.Modal.getInstance(document.getElementById('loginModal')).hide();
        
        // 更新登录按钮为头像+姓名样式
        const loginBtn = document.querySelector('[onclick="showLoginModal()"]');
        loginBtn.innerHTML = `
            <img src="${investor.avatar}" alt="${investor.name}" 
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

// 显示投资人页面
function showInvestorPage() {
    if (!currentUser) return;
    
    // 检查是否已经存在投资人页面，如果存在则先删除
    const existingInvestorPage = document.querySelector('.investor-page');
    if (existingInvestorPage) {
        existingInvestorPage.remove();
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
                                <img src="${currentUser.avatar}" alt="${currentUser.name}" 
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
                                <h3 class="text-white">${currentUser.initialAmount - currentUser.remainingAmount}万元</h3>
                            </div>
                        </div>
                    </div>
                    
                    <!-- 投资记录 -->
                    <div class="glass-card">
                        <h4 class="text-white mb-3">
                            <i class="fas fa-history me-2"></i>投资记录
                        </h4>
                        ${currentUser.investmentHistory.length > 0 ? `
                            <div class="investment-history">
                                ${currentUser.investmentHistory.map((record, index) => `
                                    <div class="investment-record-item glass-card mb-3 fade-in" style="animation-delay: ${index * 0.1}s">
                                        <div class="row align-items-center">
                                            <div class="col-md-6">
                                                <h6 class="text-white mb-1">${record.projectName}</h6>
                                                <p class="text-light mb-1">${record.teamName} #${record.teamNumber}</p>
                                                <p class="text-info small mb-0">
                                                    <i class="fas fa-clock me-1"></i>${record.time}
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

// 返回主页面
function backToMainPage() {
    // 显示主要内容
    document.querySelector('.main-content').style.display = 'block';
    
    // 删除投资人页面
    const investorPage = document.querySelector('.investor-page');
    if (investorPage) {
        investorPage.remove();
    }
}

// 显示投资模态框
function showInvestModal(projectId) {
    if (!stageConfig[currentStage].canInvest) {
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
    
    // 检查是否为晋级项目
    const sortedProjects = sortProjects();
    const isQualified = sortedProjects.slice(0, 15).some(p => p.id === projectId);
    
    if (!isQualified) {
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
    
    // 存储当前投资的项目ID
    document.getElementById('investForm').dataset.projectId = projectId;
    
    const modal = new bootstrap.Modal(document.getElementById('investModal'));
    modal.show();
}

// 处理投资
function handleInvestment(e) {
    e.preventDefault();
    
    const projectId = parseInt(document.getElementById('investForm').dataset.projectId);
    const amount = parseInt(document.getElementById('investAmount').value);
    
    if (amount > currentUser.remainingAmount) {
        showToast('投资金额超过剩余额度！', 'error');
        return;
    }
    
    const project = projects.find(p => p.id === projectId);
    if (!project) return;
    
    // 更新项目投资信息
    project.investment += amount;
    project.investmentRecords.push({
        name: currentUser.name,
        title: currentUser.title,
        avatar: './default-avatar.svg',
        amount: amount
    });
    
    // 更新投资人信息
    currentUser.remainingAmount -= amount;
    currentUser.investmentHistory.push({
        time: new Date().toLocaleString('zh-CN'),
        projectName: project.name,
        teamName: project.teamName,
        teamNumber: project.teamNumber,
        amount: amount
    });
    
    showToast(`成功投资${amount}万元！`, 'success');
    bootstrap.Modal.getInstance(document.getElementById('investModal')).hide();
    
    // 重新渲染项目列表
    renderProjects();
    
    // 重置表单
    document.getElementById('investForm').reset();
}

// 显示投资人提示框
function showInvestorTooltip(event, name, title, amount) {
    const tooltip = document.createElement('div');
    tooltip.className = 'investor-tooltip show';
    tooltip.innerHTML = `
        <div><strong>${name}</strong></div>
        <div>${title}</div>
        <div>投资: ${amount}万元</div>
    `;
    
    document.body.appendChild(tooltip);
    
    const rect = event.target.getBoundingClientRect();
    tooltip.style.left = rect.left + 'px';
    tooltip.style.top = (rect.top - tooltip.offsetHeight - 5) + 'px';
    
    // 存储引用以便清理
    event.target._tooltip = tooltip;
}

// 隐藏投资人提示框
function hideInvestorTooltip() {
    const tooltips = document.querySelectorAll('.investor-tooltip');
    tooltips.forEach(tooltip => tooltip.remove());
}

// 显示Toast提示
function showToast(message, type = 'info') {
    const toast = document.getElementById('toast');
    const toastBody = toast.querySelector('.toast-body');
    const toastHeader = toast.querySelector('.toast-header i');
    
    toastBody.textContent = message;
    
    // 设置图标和颜色
    toastHeader.className = `fas me-2 ${
        type === 'success' ? 'fa-check-circle text-success' :
        type === 'error' ? 'fa-exclamation-circle text-danger' :
        type === 'warning' ? 'fa-exclamation-triangle text-warning' :
        'fa-info-circle text-primary'
    }`;
    
    const bsToast = new bootstrap.Toast(toast);
    bsToast.show();
}

// 模拟数据更新（实际项目中应该通过API获取）
function simulateDataUpdate() {
    if (currentStage === 'ended') return;
    
    // 随机更新UV数据
    projects.forEach(project => {
        if (Math.random() < 0.3) { // 30%概率更新
            project.uv += Math.floor(Math.random() * 100) + 1;
        }
    });
    
    renderProjects();
}

// 每30秒模拟数据更新
setInterval(simulateDataUpdate, 30000);

// 工具函数：格式化数字
function formatNumber(num) {
    return num.toLocaleString();
}

// 工具函数：生成随机颜色
function getRandomColor() {
    const colors = ['#4facfe', '#fa709a', '#667eea', '#ff6b6b', '#00f2fe', '#fee140'];
    return colors[Math.floor(Math.random() * colors.length)];
}
